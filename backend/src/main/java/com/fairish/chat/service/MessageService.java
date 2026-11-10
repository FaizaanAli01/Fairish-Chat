package com.fairish.chat.service;

import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.PaginatedResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import com.fairish.chat.repository.ConversationRepository;
import com.fairish.chat.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationService conversationService,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends and persists a message within a conversation.
     * Sender ID is derived strictly from the authenticated principal.
     * Broadcasts the saved message in real-time to the conversation topic.
     */
    @Transactional
    public MessageResponse sendMessage(String conversationId, String currentUserId, SendMessageRequest request) {
        // Validate conversation exists and user is a participant
        Conversation conversation = conversationService.getConversationAndValidateParticipant(conversationId, currentUserId);

        String trimmedContent = request.getContent() != null ? request.getContent().trim() : "";
        if (trimmedContent.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be blank");
        }
        if (trimmedContent.length() > 2000) {
            throw new IllegalArgumentException("Message cannot exceed 2000 characters");
        }

        Instant now = Instant.now();

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(currentUserId)
                .content(trimmedContent)
                .createdAt(now)
                .build();

        Message savedMessage = messageRepository.save(message);

        // Update conversation summary
        conversation.setLastMessageText(trimmedContent);
        conversation.setLastMessageSenderId(currentUserId);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        log.info("Persisted message [{}] in conversation [{}] from sender [{}]",
                savedMessage.getId(), conversationId, currentUserId);

        MessageResponse response = MessageResponse.fromMessage(savedMessage);

        // Broadcast to conversation topic in real time for subscribed participants
        try {
            String destination = "/topic/conversations/" + conversationId;
            messagingTemplate.convertAndSend(destination, response);
            log.info("Broadcasted message [{}] to destination [{}]", savedMessage.getId(), destination);
        } catch (Exception ex) {
            log.error("Failed to broadcast message [{}] to conversation [{}]: {}",
                    savedMessage.getId(), conversationId, ex.getMessage(), ex);
        }

        return response;
    }

    /**
     * Loads paginated message history for a conversation.
     * Messages are retrieved in reverse chronological order (newest first for page 0),
     * enabling standard chat "load older messages" pagination.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<MessageResponse> getMessageHistory(String conversationId, String currentUserId, int page, int size) {
        // Validate conversation exists and user is a participant
        conversationService.getConversationAndValidateParticipant(conversationId, currentUserId);

        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.min(Math.max(size, 1), 100); // Between 1 and 100

        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedSize);
        Page<Message> messagePage = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        Page<MessageResponse> responsePage = messagePage.map(MessageResponse::fromMessage);
        return PaginatedResponse.fromPage(responsePage);
    }
}
