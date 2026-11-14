package com.fairish.chat.service;

import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.PaginatedResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import com.fairish.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
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

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationUnreadService conversationUnreadService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    private Conversation sampleConversation;

    @BeforeEach
    void setUp() {
        sampleConversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-1", "user-2"))
                .participantKey("user-1:user-2")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should send and persist message, updating conversation summary and broadcasting to topic")
    void shouldSendMessageSuccessfully() {
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
                .lastMessageText("Hello, World!")
                .lastMessageSenderId("user-1")
                .unreadCounts(Map.of("user-2", 1))
                .createdAt(sampleConversation.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
        when(conversationUnreadService.recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-1"), eq("user-2"), eq("Hello, World!"), any(Instant.class)))
                .thenReturn(updatedConversation);

        SendMessageRequest request = new SendMessageRequest("Hello, World!");
        MessageResponse response = messageService.sendMessage("conv-123", "user-1", request);

        assertNotNull(response);
        assertEquals("msg-1", response.getId());
        assertEquals("conv-123", response.getConversationId());
        assertEquals("user-1", response.getSenderId());
        assertEquals("Hello, World!", response.getContent());

        // Only the recipient's persisted counter is incremented, atomically with
        // the conversation summary.
        verify(conversationUnreadService, times(1)).recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-1"), eq("user-2"), eq("Hello, World!"), any(Instant.class));
        verify(conversationUnreadService, never()).recordMessageAndIncrementUnread(
                anyString(), anyString(), eq("user-1"), anyString(), any(Instant.class));

        // Real-time broadcast to the conversation topic plus an authoritative
        // unread update to the recipient's private queue.
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-123"), any(MessageResponse.class));
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
                eq("conv-123"), eq("user-2"), eq("user-1"), eq("Reply"), any(Instant.class)))
                .thenReturn(updatedConversation);

        messageService.sendMessage("conv-123", "user-2", new SendMessageRequest("Reply"));

        verify(conversationUnreadService).recordMessageAndIncrementUnread(
                eq("conv-123"), eq("user-2"), eq("user-1"), eq("Reply"), any(Instant.class));
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
    @DisplayName("Should retrieve paginated message history for valid participant")
    void shouldGetMessageHistory() {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-1"))
                .thenReturn(sampleConversation);

        Message msg1 = Message.builder()
                .id("msg-1")
                .conversationId("conv-123")
                .senderId("user-1")
                .content("First")
                .createdAt(Instant.now())
                .build();

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
}
