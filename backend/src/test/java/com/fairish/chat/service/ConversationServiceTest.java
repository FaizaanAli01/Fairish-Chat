package com.fairish.chat.service;

import com.fairish.chat.dto.ConversationResponse;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.ConversationRepository;
import com.fairish.chat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ConversationService conversationService;

    private User user1;
    private User user2;
    private User user3;
    private Conversation sampleConversation;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .id("user-1-id")
                .username("alice")
                .email("alice@example.com")
                .build();

        user2 = User.builder()
                .id("user-2-id")
                .username("bob")
                .email("bob@example.com")
                .build();

        user3 = User.builder()
                .id("user-3-id")
                .username("charlie")
                .email("charlie@example.com")
                .build();

        String key = Conversation.computeParticipantKey("user-1-id", "user-2-id");
        sampleConversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1-id", "user-2-id"))
                .participantKey(key)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should create new conversation between two distinct registered users")
    void shouldCreateNewConversation() {
        when(userRepository.findById("user-2-id")).thenReturn(Optional.of(user2));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(true);
        when(conversationRepository.findByParticipantKey(anyString())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId("conv-new");
            return c;
        });

        ConversationResponse response = conversationService.getOrCreateConversation("user-1-id", "user-2-id");

        assertNotNull(response);
        assertEquals("conv-new", response.getId());
        assertEquals(2, response.getParticipantIds().size());
        assertNotNull(response.getOtherUser());
        assertEquals("bob", response.getOtherUser().getUsername());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should return existing conversation if one already exists (idempotent / duplicate prevention)")
    void shouldReturnExistingConversation() {
        when(userRepository.findById("user-2-id")).thenReturn(Optional.of(user2));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(true);
        when(conversationRepository.findByParticipantKey(anyString())).thenReturn(Optional.of(sampleConversation));

        ConversationResponse response = conversationService.getOrCreateConversation("user-1-id", "user-2-id");

        assertNotNull(response);
        assertEquals("conv-123", response.getId());
        assertEquals("bob", response.getOtherUser().getUsername());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should disallow creating a conversation with oneself")
    void shouldRejectSelfConversation() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                conversationService.getOrCreateConversation("user-1-id", "user-1-id")
        );
        assertTrue(ex.getMessage().contains("Cannot create a conversation with yourself"));
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if recipient does not exist")
    void shouldThrowWhenRecipientNotFound() {
        when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                conversationService.getOrCreateConversation("user-1-id", "non-existent")
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle concurrent creation race condition gracefully")
    void shouldHandleConcurrentDuplicateKeyException() {
        when(userRepository.findById("user-2-id")).thenReturn(Optional.of(user2));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(true);
        when(conversationRepository.findByParticipantKey(anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(sampleConversation));
        when(conversationRepository.save(any(Conversation.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        ConversationResponse response = conversationService.getOrCreateConversation("user-1-id", "user-2-id");

        assertNotNull(response);
        assertEquals("conv-123", response.getId());
    }

    @Test
    @DisplayName("Should list user's conversations ordered by updatedAt desc")
    void shouldListUserConversations() {
        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc("user-1-id"))
                .thenReturn(List.of(sampleConversation));
        when(contactService.getAcceptedContactIds("user-1-id")).thenReturn(Set.of("user-2-id"));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(user2));

        List<ConversationResponse> list = conversationService.getUserConversations("user-1-id");

        assertEquals(1, list.size());
        assertEquals("conv-123", list.get(0).getId());
        assertEquals("bob", list.get(0).getOtherUser().getUsername());
    }

    @Test
    @DisplayName("Should reject access to conversation for non-participants with AccessDeniedException")
    void shouldRejectNonParticipant() {
        when(conversationRepository.findById("conv-123")).thenReturn(Optional.of(sampleConversation));

        assertThrows(AccessDeniedException.class, () ->
                conversationService.getConversationById("conv-123", "user-3-id")
        );
    }

    @Test
    @DisplayName("Should allow participant to get conversation by id")
    void shouldAllowParticipantToGetConversation() {
        when(conversationRepository.findById("conv-123")).thenReturn(Optional.of(sampleConversation));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(true);
        when(userRepository.findById("user-2-id")).thenReturn(Optional.of(user2));

        ConversationResponse response = conversationService.getConversationById("conv-123", "user-1-id");

        assertNotNull(response);
        assertEquals("conv-123", response.getId());
    }

    @Test
    @DisplayName("Should deny conversation creation when no accepted contact exists (none/pending/declined/removed)")
    void shouldDenyConversationCreationWithoutAcceptedContact() {
        when(userRepository.findById("user-2-id")).thenReturn(Optional.of(user2));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                conversationService.getOrCreateConversation("user-1-id", "user-2-id")
        );

        assertTrue(ex.getMessage().contains("accepted contacts"));
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should hide conversations whose counterpart is not an accepted contact")
    void shouldHideConversationsWithoutAcceptedContact() {
        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc("user-1-id"))
                .thenReturn(List.of(sampleConversation));
        when(contactService.getAcceptedContactIds("user-1-id")).thenReturn(Set.of("someone-else"));

        List<ConversationResponse> list = conversationService.getUserConversations("user-1-id");

        assertTrue(list.isEmpty());
        verify(userRepository, never()).findAllById(anySet());
    }

    @Test
    @DisplayName("Should return no conversations when the user has no accepted contacts")
    void shouldReturnNoConversationsWithoutAcceptedContacts() {
        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc("user-1-id"))
                .thenReturn(List.of(sampleConversation));
        when(contactService.getAcceptedContactIds("user-1-id")).thenReturn(Set.of());

        List<ConversationResponse> list = conversationService.getUserConversations("user-1-id");

        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("Should revoke access to an existing conversation after the contact is removed")
    void shouldRevokeAccessAfterContactRemoval() {
        when(conversationRepository.findById("conv-123")).thenReturn(Optional.of(sampleConversation));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                conversationService.getConversationById("conv-123", "user-1-id")
        );

        assertTrue(ex.getMessage().contains("accepted contacts"));
    }

    @Test
    @DisplayName("Should deny access to an existing conversation when the request is still pending")
    void shouldDenyAccessWhileRequestIsPending() {
        when(conversationRepository.findById("conv-123")).thenReturn(Optional.of(sampleConversation));
        when(contactService.areAcceptedContacts("user-1-id", "user-2-id")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                conversationService.getConversationAndValidateParticipant("conv-123", "user-1-id")
        );
    }
}
