package com.fairish.chat.service;

import com.fairish.chat.dto.ConversationResponse;
import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.encryption.MessageEncryptionService;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.ConversationRepository;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.util.AfterCommit;
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
    private final ContactService contactService;
    private final ConversationUnreadService conversationUnreadService;
    private final MessageReceiptService messageReceiptService;
    private final MessageEncryptionService encryptionService;

    public ConversationService(ConversationRepository conversationRepository,
                               UserRepository userRepository,
                               ContactService contactService,
                               ConversationUnreadService conversationUnreadService,
                               MessageReceiptService messageReceiptService,
                               MessageEncryptionService encryptionService) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.contactService = contactService;
        this.conversationUnreadService = conversationUnreadService;
        this.messageReceiptService = messageReceiptService;
        this.encryptionService = encryptionService;
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

        requireAcceptedContact(currentUserId, trimmedRecipientId);

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
     * Lists the conversations with accepted contacts only, ordered by most recent activity.
     *
     * <p>Conversations whose counterpart is not (or no longer) an accepted contact
     * are omitted rather than deleted; accepting the contact again restores access
     * to the preserved history.</p>
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(String currentUserId) {
        List<Conversation> conversations = conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> acceptedContactIds = contactService.getAcceptedContactIds(currentUserId);
        if (acceptedContactIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Conversation> visibleConversations = conversations.stream()
                .filter(c -> {
                    String otherId = c.getOtherParticipantId(currentUserId);
                    return otherId != null && acceptedContactIds.contains(otherId);
                })
                .collect(Collectors.toList());
        if (visibleConversations.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect other user IDs for batch loading
        Set<String> otherUserIds = visibleConversations.stream()
                .map(c -> c.getOtherParticipantId(currentUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return visibleConversations.stream()
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
     * Marks the conversation as viewed for the current user by resetting only
     * their unread counter. The contact/participant checks are the same as all
     * other conversation access paths.
     */
    @Transactional
    public ConversationResponse markConversationRead(String conversationId, String currentUserId) {
        Conversation conversation = getConversationAndValidateParticipant(conversationId, currentUserId);
        Conversation updated = conversationUnreadService.resetUnread(conversationId, currentUserId);
        Conversation effective = updated != null ? updated : conversation;

        String otherId = effective.getOtherParticipantId(currentUserId);
        User otherUser = otherId != null ? userRepository.findById(otherId).orElse(null) : null;

        // Opening the conversation also means every message sent by the other
        // participant has been delivered and viewed. This reuses the existing
        // read endpoint instead of adding a second seen API.
        if (otherId != null) {
            messageReceiptService.markConversationSeen(conversationId, currentUserId, otherId);
        }

        // Keep every open session of the same user in sync (including the one
        // that did not initiate the read action). Publish after commit so a
        // rolled-back read cannot reset the badge.
        AfterCommit.run(() ->
                conversationUnreadService.notifyUnreadCount(currentUserId, conversationId, 0));

        return mapToResponse(effective, currentUserId, otherUser);
    }

    /**
     * Validates that the conversation exists, that currentUserId is one of the
     * participants, and that both participants are still accepted contacts.
     *
     * <p>This is the single chat authorization primitive shared by REST history,
     * REST message sending and the STOMP subscription/send guards, so the
     * contact requirement cannot be bypassed through a different entry point.</p>
     */
    public Conversation getConversationAndValidateParticipant(String conversationId, String currentUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.hasParticipant(currentUserId)) {
            log.warn("User [{}] attempted unauthorized access to conversation [{}]", currentUserId, conversationId);
            throw new AccessDeniedException("You are not a participant in this conversation");
        }

        String otherParticipantId = conversation.getOtherParticipantId(currentUserId);
        if (otherParticipantId == null || !contactService.areAcceptedContacts(currentUserId, otherParticipantId)) {
            log.warn("User [{}] attempted to access conversation [{}] without an accepted contact relationship",
                    currentUserId, conversationId);
            throw new AccessDeniedException("Chat is only available between accepted contacts");
        }

        return conversation;
    }

    private void requireAcceptedContact(String currentUserId, String otherUserId) {
        if (!contactService.areAcceptedContacts(currentUserId, otherUserId)) {
            log.warn("User [{}] attempted to chat with [{}] without an accepted contact relationship",
                    currentUserId, otherUserId);
            throw new AccessDeniedException("Chat is only available between accepted contacts");
        }
    }

    private ConversationResponse mapToResponse(Conversation conversation, String currentUserId, User otherUser) {
        PublicUserResponse otherUserResponse = otherUser != null ? PublicUserResponse.fromUser(otherUser) : null;

        return ConversationResponse.builder()
                .id(conversation.getId())
                .participantIds(conversation.getParticipantIds())
                .otherUser(otherUserResponse)
                .lastMessageText(encryptionService.decryptConversationPreview(conversation))
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount(currentUserId))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
