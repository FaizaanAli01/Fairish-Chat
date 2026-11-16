package com.fairish.chat.service;

import com.fairish.chat.dto.MessageDeliveryResponse;
import com.fairish.chat.dto.MessageReceiptUpdate;
import com.fairish.chat.model.Message;
import com.fairish.chat.model.MessageStatus;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.mongodb.client.result.UpdateResult;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageReceiptServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageReceiptService receiptService;

    private User sender;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .build();
    }

    @Test
    @DisplayName("Should mark only incoming messages delivered, de-duplicate IDs and notify the sender")
    void shouldMarkDeliveredIdempotently() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of(message("msg-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);

        MessageDeliveryResponse response = receiptService.markDelivered(
                "conv-123", "user-2", "user-1",
                List.of("msg-1", " msg-1 ", "not a valid id!"));

        assertEquals(MessageStatus.DELIVERED, response.getStatus());
        assertEquals(List.of("msg-1"), response.getDeliveredMessageIds());

        ArgumentCaptor<Query> findCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findCaptor.capture(), eq(Message.class));

        Document query = findCaptor.getValue().getQueryObject();
        assertTrue(query.containsKey("_id"));
        assertEquals("conv-123", query.get("conversationId"));
        assertEquals("user-1", query.get("senderId"), "Only the other participant's messages may be acknowledged");
        assertTrue(query.containsKey("deliveredAt"));
        assertNull(query.get("deliveredAt"), "The update must be conditional on an unset deliveredAt");

        ArgumentCaptor<Query> updateQueryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(
                updateQueryCaptor.capture(), updateCaptor.capture(), eq(Message.class));

        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertNotNull(set.get("deliveredAt"));
        assertFalse(set.containsKey("seenAt"));

        ArgumentCaptor<MessageReceiptUpdate> receiptCaptor =
                ArgumentCaptor.forClass(MessageReceiptUpdate.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"), eq("/queue/receipts"), receiptCaptor.capture());
        MessageReceiptUpdate receipt = receiptCaptor.getValue();
        assertEquals("conv-123", receipt.getConversationId());
        assertEquals(MessageStatus.DELIVERED, receipt.getStatus());
        assertEquals(List.of("msg-1"), receipt.getMessageIds());
        assertNotNull(receipt.getOccurredAt());
    }

    @Test
    @DisplayName("Should make repeated acknowledgements a no-op and push at most one receipt")
    void shouldIgnoreDuplicateAcknowledgements() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of(message("msg-1")), List.of());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);

        MessageDeliveryResponse first = receiptService.markDelivered(
                "conv-123", "user-2", "user-1", List.of("msg-1"));
        MessageDeliveryResponse second = receiptService.markDelivered(
                "conv-123", "user-2", "user-1", List.of("msg-1"));

        assertEquals(List.of("msg-1"), first.getDeliveredMessageIds());
        assertTrue(second.getDeliveredMessageIds().isEmpty());
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("alice"), eq("/queue/receipts"), any(MessageReceiptUpdate.class));
    }

    @Test
    @DisplayName("Should ignore foreign message IDs and never notify for them")
    void shouldIgnoreForeignMessageIds() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of());

        MessageDeliveryResponse response = receiptService.markDelivered(
                "conv-123", "user-2", "user-1", List.of("someone-elses-message"));

        assertTrue(response.getDeliveredMessageIds().isEmpty());
        verifyNoInteractions(userRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Should never let a user acknowledge their own message")
    void shouldRejectSelfAcknowledgement() {
        MessageDeliveryResponse response = receiptService.markDelivered(
                "conv-123", "user-1", "user-1", List.of("msg-1"));

        assertTrue(response.getDeliveredMessageIds().isEmpty());
        verifyNoInteractions(mongoTemplate, userRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Should reject more than the maximum number of identifiers")
    void shouldRejectOversizedAcknowledgement() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i <= com.fairish.chat.dto.MessageDeliveryRequest.MAX_MESSAGE_IDS; i++) {
            ids.add("msg-" + i);
        }

        assertThrows(IllegalArgumentException.class, () ->
                receiptService.markDelivered("conv-123", "user-2", "user-1", ids));
        verifyNoInteractions(mongoTemplate, messagingTemplate);
    }

    @Test
    @DisplayName("Should mark offline messages delivered and seen when the conversation is opened")
    void shouldMarkConversationSeenForOfflineMessages() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);

        Instant seenAt = receiptService.markConversationSeen("conv-123", "user-2", "user-1");

        assertNotNull(seenAt);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).updateMulti(
                queryCaptor.capture(), updateCaptor.capture(), eq(Message.class));

        Document deliveryQuery = queryCaptor.getAllValues().get(0).getQueryObject();
        assertEquals("conv-123", deliveryQuery.get("conversationId"));
        assertEquals("user-1", deliveryQuery.get("senderId"));
        assertTrue(deliveryQuery.containsKey("createdAt"),
                "Seen must only apply to messages created at or before the watermark");
        assertTrue(deliveryQuery.containsKey("deliveredAt"));
        Document deliverySet = (Document) updateCaptor.getAllValues().get(0).getUpdateObject().get("$set");
        assertEquals(seenAt, deliverySet.get("deliveredAt"));

        Document seenQuery = queryCaptor.getAllValues().get(1).getQueryObject();
        assertTrue(seenQuery.containsKey("seenAt"));
        Document seenSet = (Document) updateCaptor.getAllValues().get(1).getUpdateObject().get("$set");
        assertEquals(seenAt, seenSet.get("seenAt"));

        ArgumentCaptor<MessageReceiptUpdate> receiptCaptor =
                ArgumentCaptor.forClass(MessageReceiptUpdate.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"), eq("/queue/receipts"), receiptCaptor.capture());
        assertEquals(MessageStatus.SEEN, receiptCaptor.getValue().getStatus());
        assertNull(receiptCaptor.getValue().getMessageIds(),
                "SEEN is a bounded conversation-level watermark, not a message list");
    }

    @Test
    @DisplayName("Should not push a receipt when the conversation has no unseen messages")
    void shouldSkipSeenReceiptWhenNothingChanged() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        Instant seenAt = receiptService.markConversationSeen("conv-123", "user-2", "user-1");

        assertNotNull(seenAt);
        verifyNoInteractions(userRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Should defer receipt pushes until the surrounding transaction commits")
    void shouldPushOnlyAfterCommit() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of(message("msg-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);

        TransactionSynchronizationManager.initSynchronization();
        try {
            receiptService.markDelivered("conv-123", "user-2", "user-1", List.of("msg-1"));

            verify(messagingTemplate, never()).convertAndSendToUser(
                    anyString(), anyString(), any(MessageReceiptUpdate.class));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("alice"), eq("/queue/receipts"), any(MessageReceiptUpdate.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Should not fail the acknowledgement when the broker push fails")
    void shouldSwallowNotificationFailures() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of(message("msg-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messagingTemplate)
                .convertAndSendToUser(eq("alice"), eq("/queue/receipts"), any(MessageReceiptUpdate.class));

        assertDoesNotThrow(() -> receiptService.markDelivered(
                "conv-123", "user-2", "user-1", List.of("msg-1")));
    }

    @Test
    @DisplayName("Should skip the push safely when the broker template is not available yet")
    void shouldSkipPushWhenTemplateUnavailable() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class)))
                .thenReturn(List.of(message("msg-1")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sender));
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> receiptService.markDelivered(
                "conv-123", "user-2", "user-1", List.of("msg-1")));
        verifyNoInteractions(messagingTemplate);
    }

    private Message message(String id) {
        return Message.builder()
                .id(id)
                .conversationId("conv-123")
                .senderId("user-1")
                .createdAt(Instant.now())
                .build();
    }
}
