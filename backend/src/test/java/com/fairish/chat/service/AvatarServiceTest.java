package com.fairish.chat.service;

import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.AvatarStorageException;
import com.fairish.chat.exception.InvalidAvatarException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.storage.AvatarStorage;
import com.fairish.chat.storage.StoredAvatar;
import com.fairish.chat.support.AvatarTestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvatarStorage avatarStorage;

    @Mock
    private ContactService contactService;

    private AvatarService avatarService;

    @BeforeEach
    void setUp() {
        AvatarImageProcessor processor = new AvatarImageProcessor(5 * 1024 * 1024, 512);
        avatarService = new AvatarService(userRepository, avatarStorage, processor, contactService);
    }

    // ---------------------------------------------------------------------
    // Upload
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Upload stores an opaque key, replaces the pointer, and clears the legacy profileImage URL")
    void shouldUploadAvatar() {
        User user = userWithLegacyImage("user-1", "avatars/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = avatarService.uploadAvatar("user-1", pngFile());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(avatarStorage).put(keyCaptor.capture(), bytesCaptor.capture(), contentTypeCaptor.capture());

        String newKey = keyCaptor.getValue();
        assertTrue(newKey.matches("avatars/[0-9a-fA-F-]{36}\\.png"),
                "storage key should be an opaque UUID, was: " + newKey);
        assertNotEquals("avatars/old.png", newKey);
        assertEquals("image/png", contentTypeCaptor.getValue());
        assertTrue(bytesCaptor.getValue().length > 0);

        assertEquals(newKey, user.getAvatarKey());
        assertEquals("image/png", user.getAvatarContentType());
        assertNotNull(user.getAvatarUpdatedAt());
        assertNull(user.getProfileImage(), "uploading an avatar must clear the legacy URL");

        assertTrue(response.isHasAvatar());
        assertNull(response.getProfileImage());
        assertNotNull(response.getAvatarUpdatedAt());

        verify(avatarStorage).delete("avatars/old.png");
    }

    @Test
    @DisplayName("JPEG upload uses a .jpg key and image/jpeg content type")
    void shouldUploadJpeg() {
        User user = userWithLegacyImage("user-1", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        avatarService.uploadAvatar("user-1", new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", AvatarTestImages.jpeg(300, 300)));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).put(keyCaptor.capture(), any(byte[].class), contentTypeCaptor.capture());

        assertTrue(keyCaptor.getValue().endsWith(".jpg"));
        assertEquals("image/jpeg", contentTypeCaptor.getValue());
        verify(avatarStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("Upload rejects an empty file before touching storage")
    void shouldRejectEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThrows(InvalidAvatarException.class, () -> avatarService.uploadAvatar("user-1", empty));

        verifyNoInteractions(avatarStorage, userRepository);
    }

    @Test
    @DisplayName("Upload rejects an oversized file before reading it into memory")
    void shouldRejectOversizedUpload() {
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(6L * 1024 * 1024);

        assertThrows(InvalidAvatarException.class, () -> avatarService.uploadAvatar("user-1", oversized));

        verifyNoInteractions(avatarStorage, userRepository);
    }

    @Test
    @DisplayName("Upload rejects non-image content before touching storage")
    void shouldRejectNonImageUpload() {
        MockMultipartFile text = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "not an image".getBytes());

        assertThrows(InvalidAvatarException.class, () -> avatarService.uploadAvatar("user-1", text));

        verifyNoInteractions(avatarStorage, userRepository);
    }

    @Test
    @DisplayName("A storage failure aborts the upload without mutating the profile")
    void shouldPropagateStorageFailure() {
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(userWithLegacyImage("user-1", null)));
        org.mockito.Mockito.doThrow(new AvatarStorageException("storage down"))
                .when(avatarStorage).put(anyString(), any(byte[].class), anyString());

        assertThrows(AvatarStorageException.class, () -> avatarService.uploadAvatar("user-1", pngFile()));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("If the profile save fails, the newly written object is removed and the old one is kept")
    void shouldCleanUpNewObjectWhenSaveFails() {
        User user = userWithLegacyImage("user-1", "avatars/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("database down"));

        assertThrows(RuntimeException.class, () -> avatarService.uploadAvatar("user-1", pngFile()));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).put(keyCaptor.capture(), any(byte[].class), anyString());

        verify(avatarStorage).delete(keyCaptor.getValue());
        verify(avatarStorage, never()).delete("avatars/old.png");
    }

    // ---------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Delete clears the avatar pointer and removes the stored object")
    void shouldDeleteAvatar() {
        User user = userWithLegacyImage("user-1", "avatars/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = avatarService.deleteAvatar("user-1");

        assertNull(user.getAvatarKey());
        assertNull(user.getAvatarContentType());
        assertNull(user.getAvatarUpdatedAt());
        assertFalse(response.isHasAvatar());
        verify(avatarStorage).delete("avatars/old.png");
    }

    @Test
    @DisplayName("Delete is a no-op when the user has no avatar")
    void shouldNoOpDeleteWithoutAvatar() {
        User user = userWithLegacyImage("user-1", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserResponse response = avatarService.deleteAvatar("user-1");

        assertFalse(response.isHasAvatar());
        verifyNoInteractions(avatarStorage);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Delete still clears the pointer when object storage cleanup fails")
    void shouldTolerateStorageFailureOnDelete() {
        User user = userWithLegacyImage("user-1", "avatars/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new AvatarStorageException("storage down"))
                .when(avatarStorage).delete("avatars/old.png");

        UserResponse response = avatarService.deleteAvatar("user-1");

        assertFalse(response.isHasAvatar());
        assertNull(user.getAvatarKey());
    }

    // ---------------------------------------------------------------------
    // Authorized delivery matrix
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Owner can read their own avatar")
    void ownerCanReadAvatar() {
        User target = userWithAvatar("user-1", "avatars/mine.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(avatarStorage.get("avatars/mine.png"))
                .thenReturn(Optional.of(new StoredAvatar(new byte[]{1, 2, 3}, "image/png")));

        AvatarContent content = avatarService.getAvatar("user-1", "user-1");

        assertEquals(3, content.content().length);
        assertEquals("image/png", content.contentType());
        verifyNoInteractions(contactService);
    }

    @Test
    @DisplayName("An accepted contact can read the avatar")
    void acceptedContactCanReadAvatar() {
        User target = userWithAvatar("user-2", "avatars/theirs.png");
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts("user-1", "user-2")).thenReturn(true);
        when(avatarStorage.get("avatars/theirs.png"))
                .thenReturn(Optional.of(new StoredAvatar(new byte[]{9}, "image/jpeg")));

        AvatarContent content = avatarService.getAvatar("user-1", "user-2");

        assertEquals(1, content.content().length);
        assertEquals("image/png", content.contentType());
    }

    @Test
    @DisplayName("A pending contact request does not grant avatar access")
    void pendingContactCannotReadAvatar() {
        assertDenied("user-1", "user-2");
    }

    @Test
    @DisplayName("Conversation participation without an accepted contact does not grant avatar access")
    void conversationParticipantCannotReadAvatar() {
        assertDenied("user-1", "user-3");
    }

    @Test
    @DisplayName("A stranger cannot read the avatar")
    void strangerCannotReadAvatar() {
        assertDenied("user-1", "user-4");
    }

    @Test
    @DisplayName("A removed contact cannot read the avatar")
    void removedContactCannotReadAvatar() {
        assertDenied("user-1", "user-5");
    }

    @Test
    @DisplayName("A missing target user yields 404 semantics")
    void missingTargetUserIsNotFound() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> avatarService.getAvatar("user-1", "ghost"));
        verifyNoInteractions(avatarStorage, contactService);
    }

    @Test
    @DisplayName("A user without an avatar yields 404 semantics")
    void missingAvatarIsNotFound() {
        User target = userWithLegacyImage("user-2", null);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts("user-1", "user-2")).thenReturn(true);

        assertThrows(ResourceNotFoundException.class,
                () -> avatarService.getAvatar("user-1", "user-2"));
        verifyNoInteractions(avatarStorage);
    }

    @Test
    @DisplayName("A pointer to a missing storage object yields 404 semantics")
    void missingStoredObjectIsNotFound() {
        User target = userWithAvatar("user-2", "avatars/gone.png");
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts("user-1", "user-2")).thenReturn(true);
        when(avatarStorage.get("avatars/gone.png")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> avatarService.getAvatar("user-1", "user-2"));
    }

    @Test
    @DisplayName("The content type recorded on the user is authoritative")
    void recordedContentTypeWins() {
        User target = userWithAvatar("user-2", "avatars/theirs.png");
        target.setAvatarContentType("image/png");
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts("user-1", "user-2")).thenReturn(true);
        when(avatarStorage.get("avatars/theirs.png"))
                .thenReturn(Optional.of(new StoredAvatar(new byte[]{1}, "application/octet-stream")));

        AvatarContent content = avatarService.getAvatar("user-1", "user-2");

        assertEquals("image/png", content.contentType());
    }

    @Test
    @DisplayName("The stored content type is used when the user record has none")
    void storedContentTypeIsFallback() {
        User target = userWithAvatar("user-2", "avatars/theirs.png");
        target.setAvatarContentType(null);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts("user-1", "user-2")).thenReturn(true);
        when(avatarStorage.get("avatars/theirs.png"))
                .thenReturn(Optional.of(new StoredAvatar(new byte[]{1}, "image/jpeg")));

        AvatarContent content = avatarService.getAvatar("user-1", "user-2");

        assertEquals("image/jpeg", content.contentType());
    }

    private void assertDenied(String viewerId, String targetId) {
        User target = userWithAvatar(targetId, "avatars/secret.png");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(contactService.areAcceptedContacts(viewerId, targetId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> avatarService.getAvatar(viewerId, targetId));

        verify(avatarStorage, never()).get(anyString());
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png",
                AvatarTestImages.png(600, 600));
    }

    private User userWithLegacyImage(String id, String avatarKey) {
        return User.builder()
                .id(id)
                .username("user-" + id)
                .email(id + "@example.com")
                .profileImage("https://legacy.example.com/avatar.png")
                .avatarKey(avatarKey)
                .build();
    }

    private User userWithAvatar(String id, String avatarKey) {
        return User.builder()
                .id(id)
                .username("user-" + id)
                .email(id + "@example.com")
                .avatarKey(avatarKey)
                .avatarContentType("image/png")
                .avatarUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
