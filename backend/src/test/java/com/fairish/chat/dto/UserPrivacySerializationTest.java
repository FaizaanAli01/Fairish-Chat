package com.fairish.chat.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fairish.chat.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the JSON contract for the public and self projections.
 */
class UserPrivacySerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Public user JSON never contains email, legacy profileImage, or storage metadata")
    void publicProjectionOmitsPrivateFields() throws Exception {
        User user = User.builder()
                .id("user-2")
                .username("bob")
                .email("bob@example.com")
                .profileImage("https://legacy.example.com/bob.png")
                .avatarKey("avatars/8b1d0c2e-secret.png")
                .avatarContentType("image/png")
                .avatarUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        String json = objectMapper.writeValueAsString(PublicUserResponse.fromUser(user));

        assertTrue(json.contains("\"hasAvatar\":true"));
        assertTrue(json.contains("\"avatarUpdatedAt\""));
        assertFalse(json.contains("email"));
        assertFalse(json.contains("bob@example.com"));
        assertFalse(json.contains("profileImage"));
        assertFalse(json.contains("legacy.example.com"));
        assertFalse(json.contains("avatarKey"));
        assertFalse(json.contains("avatars/"));
        assertFalse(json.contains("avatarContentType"));
        assertFalse(json.contains("image/png"));
    }

    @Test
    @DisplayName("Self profile JSON keeps the owner's email and legacy profileImage but never the storage key")
    void selfProjectionKeepsOwnerFields() throws Exception {
        User user = User.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .profileImage("https://legacy.example.com/alice.png")
                .avatarKey("avatars/8b1d0c2e-secret.png")
                .avatarContentType("image/png")
                .avatarUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        String json = objectMapper.writeValueAsString(UserResponse.fromUser(user));

        assertTrue(json.contains("\"email\":\"alice@example.com\""));
        assertTrue(json.contains("\"profileImage\":\"https://legacy.example.com/alice.png\""));
        assertTrue(json.contains("\"hasAvatar\":true"));
        assertFalse(json.contains("avatarKey"));
        assertFalse(json.contains("avatars/"));
        assertFalse(json.contains("avatarContentType"));
    }

    @Test
    @DisplayName("Legacy profileImage remains owner-visible until replaced")
    void legacyProfileImageIsOwnerOnly() {
        User legacyOnly = User.builder()
                .id("user-3")
                .username("carol")
                .email("carol@example.com")
                .profileImage("https://legacy.example.com/carol.png")
                .build();

        UserResponse self = UserResponse.fromUser(legacyOnly);
        assertEquals("https://legacy.example.com/carol.png", self.getProfileImage());
        assertFalse(self.isHasAvatar());

        PublicUserResponse publicView = PublicUserResponse.fromUser(legacyOnly);
        assertFalse(publicView.isHasAvatar());
        assertNull(publicView.getAvatarUpdatedAt());
    }

    @Test
    @DisplayName("A user with an uploaded avatar reports hasAvatar and no legacy URL")
    void uploadedAvatarReplacesLegacyUrl() {
        User user = User.builder()
                .id("user-4")
                .username("dave")
                .email("dave@example.com")
                .profileImage(null)
                .avatarKey("avatars/new-key.png")
                .avatarContentType("image/png")
                .avatarUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        UserResponse self = UserResponse.fromUser(user);

        assertTrue(self.isHasAvatar());
        assertNotNull(self.getAvatarUpdatedAt());
        assertNull(self.getProfileImage());
    }

    @Test
    @DisplayName("Null users map to null projections")
    void nullUserMapsToNull() {
        assertNull(PublicUserResponse.fromUser(null));
        assertNull(UserResponse.fromUser(null));
    }
}
