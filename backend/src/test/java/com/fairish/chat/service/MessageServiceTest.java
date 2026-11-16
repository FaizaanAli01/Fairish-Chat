package com.fairish.chat.service;

import com.fairish.chat.dto.MessageDeliveryResponse;
import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.PaginatedResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.encryption.EncryptedPayload;
import com.fairish.chat.encryption.MessageEncryptionProperties;
import com.fairish.chat.encryption.MessageEncryptionService;
import com.fairish.chat.exception.MessageDecryptionException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import com.fairish.chat.model.MessageStatus;
import com.fairish.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    static final String TEST_KEY = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVpBQkNERUY=";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationUnreadService conversationUnreadService;

    @Mock
    private MessageReceiptService messageReceiptService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MessageEncryptionService encryptionService;
    private MessageService messageService;

    private Conversation sampleConversation;

    @BeforeEach
    void setUp() {
        MessageEncryptionProperties properties = new MessageEncryptionProperties();
        properties.setEnabled(true);
        properties.setActiveKeyId("v1");
        properties.setKey(TEST_KEY);
        encryptionService = new MessageEncryptionService(properties);

        messageService = new MessageService(messageRepository, conversationService,
                conversationUnreadService, messageReceiptService, messagingTemplate, encryptionService);

        sampleConversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should encrypt before persistence and broadcast only decrypted plaintext")
    void shouldEncryptPersistAndBroadcastPlaintext() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId("msg-1");
            return m;
        });

        Conversation updatedConversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .unreadCounts(Map.of("user-2", 1))
                .createdAt(sampleConversation.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
        when(conversationUnreadService.recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-1"), eq("user-2"), any(EncryptedPayload.class), any(Instant.class)))
                .thenReturn(updatedConversation);

        SendMessageRequest request = new SendMessageRequest("Hello, World!");
        MessageResponse response = messageService.sendMessage("conv-123", "user-1", request);

        assertNotNull(response);
        assertEquals("msg-1", response.getId());
        assertEquals("conv-123", response.getConversationId());
        assertEquals("user-1", response.getSenderId());
        assertEquals("Hello, World!", response.getContent());
        assertEquals(MessageStatus.SENT, response.getStatus());
        assertNull(response.getDeliveredAt());
        assertNull(response.getSeenAt());

        ArgumentCaptor<Message> savedCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(savedCaptor.capture());
        Message saved = savedCaptor.getValue();
        assertNull(saved.getContent(), "New messages must not persist plaintext in content");
        assertNotNull(saved.getCiphertext());
        assertEquals(1, saved.getEncryptionVersion());
        assertEquals("v1", saved.getKeyId());
        assertEquals(12, Base64.getDecoder().decode(saved.getNonce()).length);
        assertEquals(16, Base64.getDecoder().decode(saved.getAuthTag()).length);
        assertNotEquals("Hello, World!", saved.getCiphertext());
        assertEquals("Hello, World!", encryptionService.decrypt(saved));

        ArgumentCaptor<EncryptedPayload> previewCaptor = ArgumentCaptor.forClass(EncryptedPayload.class);
        verify(conversationUnreadService).recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-1"), eq("user-2"), previewCaptor.capture(), any(Instant.class));
        EncryptedPayload preview = previewCaptor.getValue();
        assertEquals("Hello, World!", encryptionService.decrypt(preview));
        assertNotEquals(saved.getNonce(), preview.nonce(),
                "The preview and message must use independent nonces");
        assertNotEquals(saved.getCiphertext(), preview.ciphertext());

        ArgumentCaptor<MessageResponse> broadcastCaptor = ArgumentCaptor.forClass(MessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-123"), broadcastCaptor.capture());
        assertEquals("Hello, World!", broadcastCaptor.getValue().getContent());
        assertNotEquals(saved.getCiphertext(), broadcastCaptor.getValue().getContent());

        verify(conversationUnreadService).notifyUnreadCount("user-2", "conv-123", 1);
        verify(conversationUnreadService, never()).notifyUnreadCount(eq("user-1"), anyString(), anyInt());
    }

    @Test
    @DisplayName("Should increment the other participant's unread count when they are the recipient")
    void shouldIncrementRecipientWhenOtherUserSends() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-2"))
                .thenReturn(sampleConversation);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId("msg-2");
            return m;
        });

        Conversation updatedConversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .unreadCounts(Map.of("user-1", 3))
                .createdAt(sampleConversation.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
        when(conversationUnreadService.recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-2"), eq("user-1"), any(EncryptedPayload.class), any(Instant.class)))
                .thenReturn(updatedConversation);

        messageService.sendMessage("conv-123", "user-2", new SendMessageRequest("Reply"));

        verify(conversationUnreadService).recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-2"), eq("user-1"), any(EncryptedPayload.class), any(Instant.class));
        verify(conversationUnreadService).notifyUnreadCount("user-1", "conv-123", 3);
    }

    @Test
    @DisplayName("Should reject blank message content")
    void shouldRejectBlankMessage() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        SendMessageRequest request = new SendMessageRequest("   ");

        assertThrows(IllegalArgumentException.class, () ->
                messageService.sendMessage("conv-123", "user-1", request)
        );
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Should reject message exceeding 2000 characters")
    void shouldRejectOversizedMessage() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        String oversized = "a".repeat(2001);
        SendMessageRequest request = new SendMessageRequest(oversized);

        assertThrows(IllegalArgumentException.class, () ->
                messageService.sendMessage("conv-123", "user-1", request)
        );
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Should reject message sending if user is not a participant")
    void shouldRejectSendingIfNotParticipant() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-intruder"))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        SendMessageRequest request = new SendMessageRequest("Sneaky message");

        assertThrows(AccessDeniedException.class, () ->
                messageService.sendMessage("conv-123", "user-intruder", request)
        );
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Should decrypt paginated message history for a valid participant")
    void shouldGetDecryptedMessageHistory() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        EncryptedPayload payload = encryptionService.encrypt("First");
        Message msg1 = encryptedMessage("msg-1", payload);

        Page<Message> page = new PageImpl<>(List.of(msg1));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(page);

        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory("conv-123", "user-1", 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("First", response.getContent().get(0).getContent());
        assertEquals("msg-1", response.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Should keep legacy plaintext messages readable")
    void shouldReadLegacyPlaintextMessages() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        Message legacy = Message.builder()
                .id("legacy-1")
                .conversationId("conv-123")
                .senderId("user-1")
                .content("Legacy plaintext")
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(legacy)));

        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory("conv-123", "user-1", 0, 20);

        assertEquals("Legacy plaintext", response.getContent().get(0).getContent());
    }

    @Test
    @DisplayName("Should reject tampered ciphertext instead of returning it as plaintext")
    void shouldRejectTamperedHistory() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        EncryptedPayload payload = encryptionService.encrypt("Original");
        byte[] tampered = Base64.getDecoder().decode(payload.ciphertext());
        tampered[0] ^= 0x01;
        Message message = Message.builder()
                .id("msg-tampered")
                .conversationId("conv-123")
                .senderId("user-1")
                .ciphertext(Base64.getEncoder().encodeToString(tampered))
                .nonce(payload.nonce())
                .authTag(payload.authTag())
                .encryptionVersion(payload.encryptionVersion())
                .keyId(payload.keyId())
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));

        assertThrows(MessageDecryptionException.class,
                () -> messageService.getMessageHistory("conv-123", "user-1", 0, 20));
    }

    @Test
    @DisplayName("Should preserve pagination metadata while decrypting history")
    void shouldPreservePaginationMetadata() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        Message newest = encryptedMessage("msg-2", encryptionService.encrypt("Newest"));
        Message older = encryptedMessage("msg-1", encryptionService.encrypt("Older"));
        Page<Message> page = new PageImpl<>(List.of(newest, older), PageRequest.of(0, 2), 5);
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(page);

        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory("conv-123", "user-1", 0, 2);

        assertEquals(2, response.getContent().size());
        assertEquals(5, response.getTotalElements());
        assertTrue(response.isHasMore());
        assertEquals("Newest", response.getContent().get(0).getContent());
        assertEquals("Older", response.getContent().get(1).getContent());
    }

    @Test
    @DisplayName("Should reject message history retrieval if user is not a participant")
    void shouldRejectHistoryIfNotParticipant() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-intruder"))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        assertThrows(AccessDeniedException.class, () ->
                messageService.getMessageHistory("conv-123", "user-intruder", 0, 20)
        );
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("Should reject sending when the contact relationship is no longer accepted")
    void shouldRejectSendingWhenContactPolicyDenies() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        assertThrows(AccessDeniedException.class, () ->
                messageService.sendMessage("conv-123", "user-1", new SendMessageRequest("Hello"))
        );
        verifyNoInteractions(messageRepository, messagingTemplate);
    }

    @Test
    @DisplayName("Should reject history when the contact relationship is no longer accepted")
    void shouldRejectHistoryWhenContactPolicyDenies() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        assertThrows(AccessDeniedException.class, () ->
                messageService.getMessageHistory("conv-123", "user-1", 0, 20)
        );
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("Should derive DELIVERED and SEEN status while still decrypting content")
    void shouldDeriveReceiptStatusWithoutBreakingEncryption() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        Instant deliveredAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant seenAt = Instant.parse("2026-01-01T10:01:00Z");

        Message delivered = encryptedMessage("msg-delivered", encryptionService.encrypt("Delivered"));
        delivered.setDeliveredAt(deliveredAt);

        Message seen = encryptedMessage("msg-seen", encryptionService.encrypt("Seen"));
        seen.setDeliveredAt(deliveredAt);
        seen.setSeenAt(seenAt);

        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seen, delivered)));

        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory("conv-123", "user-1", 0, 20);

        assertEquals(MessageStatus.SEEN, response.getContent().get(0).getStatus());
        assertEquals(seenAt, response.getContent().get(0).getSeenAt());
        assertEquals("Seen", response.getContent().get(0).getContent());
        assertEquals(MessageStatus.DELIVERED, response.getContent().get(1).getStatus());
        assertEquals(deliveredAt, response.getContent().get(1).getDeliveredAt());
        assertEquals("Delivered", response.getContent().get(1).getContent());
    }

    @Test
    @DisplayName("Should delegate delivery acknowledgement using the validated other participant")
    void shouldDelegateDeliveryAcknowledgement() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-2"))
                .thenReturn(sampleConversation);
        MessageDeliveryResponse expected = new MessageDeliveryResponse(
                "conv-123", MessageStatus.DELIVERED, List.of("msg-1"), Instant.now());
        when(messageReceiptService.markDelivered(
                eq("conv-123"), eq("user-2"), eq("user-1"), eq(List.of("msg-1"))))
                .thenReturn(expected);

        MessageDeliveryResponse response = messageService.markDelivered(
                "conv-123", "user-2", List.of("msg-1"));

        assertSame(expected, response);
        verify(conversationService).getConversationAndValidateParticipant("conv-123", "user-2");
    }

    @Test
    @DisplayName("Should reject delivery acknowledgement from a non-participant")
    void shouldRejectDeliveryAcknowledgementFromNonParticipant() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "intruder"))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        assertThrows(AccessDeniedException.class, () ->
                messageService.markDelivered("conv-123", "intruder", List.of("msg-1"))
        );

        verifyNoInteractions(messageReceiptService);
    }

    @Test
    @DisplayName("Should gracefully handle corrupted encrypted message in history with placeholder (ENC-01)")
    void shouldGracefullyHandleCorruptedMessageInHistory() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        Message corrupted = Message.builder()
                .id("msg-corrupted")
                .conversationId("conv-123")
                .senderId("user-1")
                .ciphertext("corrupted-ciphertext")
                .nonce("corrupted-nonce")
                .authTag("corrupted-authTag")
                .encryptionVersion(1)
                .keyId("v1")
                .createdAt(Instant.now())
                .build();

        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corrupted)));

        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory("conv-123", "user-1", 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(MessageEncryptionService.PREVIEW_UNAVAILABLE, response.getContent().get(0).getContent());
    }

    private Message encryptedMessage(String id, EncryptedPayload payload) {
        return Message.builder()
                .id(id)
                .conversationId("conv-123")
                .senderId("user-1")
                .ciphertext(payload.ciphertext())
                .nonce(payload.nonce())
                .authTag(payload.authTag())
                .encryptionVersion(payload.encryptionVersion())
                .keyId(payload.keyId())
                .createdAt(Instant.now())
                .build();
    }
}
