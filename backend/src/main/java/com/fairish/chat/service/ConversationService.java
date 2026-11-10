package com.fairish.chat.service;

import com.fairish.chat.dto.ConversationResponse;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.ConversationRepository;
import com.fairish.chat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository, UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates or retrieves an existing one-to-one conversation between the current user and the recipient.
     */
    @Transactional
    public ConversationResponse getOrCreateConversation(String currentUserId, String recipientId) {
        if (recipientId == null || recipientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient ID must not be blank");
        }

        String trimmedRecipientId = recipientId.trim();

        if (currentUserId.equals(trimmedRecipientId)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }

        // Validate that recipient exists
        User recipient = userRepository.findById(trimmedRecipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found with id: " + trimmedRecipientId));

        String participantKey = Conversation.computeParticipantKey(currentUserId, trimmedRecipientId);

        // Check if conversation already exists
        Optional<Conversation> existing = conversationRepository.findByParticipantKey(participantKey);
        if (existing.isPresent()) {
            return mapToResponse(existing.get(), currentUserId, recipient);
        }

        // Create new conversation
        List<String> normalizedParticipants = Conversation.normalizeParticipants(currentUserId, trimmedRecipientId);
        Instant now = Instant.now();

        Conversation newConversation = Conversation.builder()
                .participantIds(normalizedParticipants)
                .participantKey(participantKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            Conversation saved = conversationRepository.save(newConversation);
            log.info("Created new conversation: {} between users [{}] and [{}]", saved.getId(), currentUserId, trimmedRecipientId);
            return mapToResponse(saved, currentUserId, recipient);
        } catch (DuplicateKeyException ex) {
            // In case of concurrent creation, retrieve the existing one safely
            Conversation concurrent = conversationRepository.findByParticipantKey(participantKey)
                    .orElseThrow(() -> ex);
            return mapToResponse(concurrent, currentUserId, recipient);
        }
    }

    /**
     * Lists all conversations for the authenticated user, ordered by most recent activity.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(String currentUserId) {
        List<Conversation> conversations = conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect other user IDs for batch loading
        Set<String> otherUserIds = conversations.stream()
                .map(c -> c.getOtherParticipantId(currentUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return conversations.stream()
                .map(c -> {
                    String otherId = c.getOtherParticipantId(currentUserId);
                    User otherUser = otherId != null ? userMap.get(otherId) : null;
                    return mapToResponse(c, currentUserId, otherUser);
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a conversation by ID, ensuring the authenticated user is a participant.
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(String conversationId, String currentUserId) {
        Conversation conversation = getConversationAndValidateParticipant(conversationId, currentUserId);
        String otherId = conversation.getOtherParticipantId(currentUserId);
        User otherUser = otherId != null ? userRepository.findById(otherId).orElse(null) : null;
        return mapToResponse(conversation, currentUserId, otherUser);
    }

    /**
     * Validates that the conversation exists and that currentUserId is one of the participants.
     */
    public Conversation getConversationAndValidateParticipant(String conversationId, String currentUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.hasParticipant(currentUserId)) {
            log.warn("User [{}] attempted unauthorized access to conversation [{}]", currentUserId, conversationId);
            throw new AccessDeniedException("You are not a participant in this conversation");
        }

        return conversation;
    }

    private ConversationResponse mapToResponse(Conversation conversation, String currentUserId, User otherUser) {
        UserResponse otherUserResponse = otherUser != null ? UserResponse.fromUser(otherUser) : null;

        return ConversationResponse.builder()
                .id(conversation.getId())
                .participantIds(conversation.getParticipantIds())
                .otherUser(otherUserResponse)
                .lastMessageText(conversation.getLastMessageText())
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
