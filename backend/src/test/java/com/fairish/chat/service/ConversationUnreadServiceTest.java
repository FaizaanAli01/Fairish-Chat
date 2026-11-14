package com.fairish.chat.service;

import com.fairish.chat.dto.UnreadCountUpdate;
import com.fairish.chat.encryption.EncryptedPayload;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationUnreadServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConversationUnreadService unreadService;

    private Conversation conversation;
    private User recipient;

    @BeforeEach
    void setUp() {
        lenient().when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);

        conversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .unreadCounts(Map.of("user-2", 1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        recipient = User.builder()
                .id("user-2")
                .username("bob")
                .email("bob@example.com")
                .build();
    }

    @Test
    @DisplayName("Should atomically update the summary and increment only the recipient's unread count")
    void shouldIncrementOnlyRecipientUnread() {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversation);

        Instant sentAt = Instant.parse("2026-01-01T10:00:00Z");
        EncryptedPayload preview = new EncryptedPayload("ciphertext-value", "nonce-value",
                "tag-value", 1, "v1");
        Conversation result = unreadService.recordMessageAndIncrementUnread(
                "conv-123", "user-1", "user-2", preview, sentAt);

        assertSame(conversation, result);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(Conversation.class));

        Document update = updateCaptor.getValue().getUpdateObject();
        Document set = (Document) update.get("$set");
        Document unset = (Document) update.get("$unset");
        Document inc = (Document) update.get("$inc");

        assertEquals("ciphertext-value", set.get("lastMessageCiphertext"));
        assertEquals("nonce-value", set.get("lastMessageNonce"));
        assertEquals("tag-value", set.get("lastMessageAuthTag"));
        assertEquals(1, set.get("lastMessageEncryptionVersion"));
        assertEquals("v1", set.get("lastMessageKeyId"));
        assertFalse(set.containsKey("lastMessageText"), "Preview plaintext must never be written");
        assertTrue(unset.containsKey("lastMessageText"), "Legacy plaintext preview must be removed");
        assertEquals("user-1", set.get("lastMessageSenderId"));
        assertEquals(sentAt, set.get("lastMessageAt"));
        assertEquals(1, inc.get("unreadCounts.user-2"));
        assertNull(inc.get("unreadCounts.user-1"), "The sender must never receive an unread increment");
    }

    @Test
    @DisplayName("Should reset only the current user's unread count")
    void shouldResetOnlyCurrentUserUnread() {
        Conversation reset = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .unreadCounts(Map.of("user-1", 0, "user-2", 1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(reset);

        Conversation result = unreadService.resetUnread("conv-123", "user-1");

        assertSame(reset, result);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(Conversation.class));

        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertEquals(0, set.get("unreadCounts.user-1"));
        assertFalse(set.containsKey("unreadCounts.user-2"));
    }

    @Test
    @DisplayName("Should push the authoritative count to every session of the recipient")
    void shouldPushUnreadCountToUserQueue() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(recipient));

        unreadService.notifyUnreadCount("user-2", "conv-123", 2);

        ArgumentCaptor<UnreadCountUpdate> payloadCaptor = ArgumentCaptor.forClass(UnreadCountUpdate.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("bob"), eq("/queue/unread"), payloadCaptor.capture());
        assertEquals("conv-123", payloadCaptor.getValue().getConversationId());
        assertEquals(2, payloadCaptor.getValue().getUnreadCount());
    }

    @Test
    @DisplayName("Should not fail the originating operation when unread notification fails")
    void shouldSwallowNotificationFailures() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(recipient));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messagingTemplate)
                .convertAndSendToUser(eq("bob"), eq("/queue/unread"), any(UnreadCountUpdate.class));

        assertDoesNotThrow(() -> unreadService.notifyUnreadCount("user-2", "conv-123", 1));
    }

    @Test
    @DisplayName("Should skip the push safely when the broker template is not available yet")
    void shouldSkipPushWhenTemplateUnavailable() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(null);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(recipient));

        assertDoesNotThrow(() -> unreadService.notifyUnreadCount("user-2", "conv-123", 1));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Should reject user IDs that could inject MongoDB field paths")
    void shouldRejectUnsafeUserIds() {
        assertThrows(IllegalArgumentException.class,
                () -> unreadService.resetUnread("conv-123", "user.with.dot"));
    }
}
