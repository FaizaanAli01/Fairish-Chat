package com.fairish.chat.service;

import com.fairish.chat.dto.MessageDeliveryRequest;
import com.fairish.chat.dto.MessageDeliveryResponse;
import com.fairish.chat.dto.MessageReceiptUpdate;
import com.fairish.chat.model.Message;
import com.fairish.chat.model.MessageStatus;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.util.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists per-message delivery receipts and pushes them to the sender.
 *
 * <p>This service deliberately does not depend on {@code ConversationService}.
 * Conversation authorization is performed by the caller through the single
 * existing participant/contact primitive, and only the already-validated
 * conversation identities are passed in. That keeps this bean out of the
 * {@code WebSocketConfig -> WebSocketAuthInterceptor -> ConversationService}
 * dependency cycle and lets {@code ConversationService} call it for the
 * existing read flow.</p>
 *
 * <p>All updates are conditional on the previous timestamp being absent, so
 * duplicate acknowledgements are no-ops and a message can only move forward:
 * SENT -> DELIVERED -> SEEN. Message content is never read, decrypted or
 * returned here.</p>
 */
@Service
public class MessageReceiptService {

    private static final Logger log = LoggerFactory.getLogger(MessageReceiptService.class);
    private static final String RECEIPT_QUEUE = "/queue/receipts";
    private static final String SAFE_MESSAGE_ID = "[A-Za-z0-9_-]{1,64}";

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    public MessageReceiptService(MongoTemplate mongoTemplate,
                                 UserRepository userRepository,
                                 ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    /**
     * Marks the given messages as delivered for the recipient.
     *
     * @param conversationId conversation that owns the messages
     * @param recipientId    authenticated user acknowledging receipt
     * @param senderId       the other participant; only their messages may be acknowledged
     * @param messageIds     client-supplied identifiers; foreign/invalid ones are ignored
     */
    public MessageDeliveryResponse markDelivered(String conversationId,
                                                 String recipientId,
                                                 String senderId,
                                                 List<String> messageIds) {
        Instant deliveredAt = Instant.now();
        List<String> updatedIds = new ArrayList<>();

        // A user can never acknowledge their own message, and a malformed
        // one-to-one conversation has no valid sender to update or notify.
        if (senderId == null || senderId.equals(recipientId)) {
            return new MessageDeliveryResponse(conversationId, MessageStatus.DELIVERED,
                    updatedIds, deliveredAt);
        }

        Set<String> sanitized = sanitizeMessageIds(messageIds);
        if (sanitized.isEmpty()) {
            return new MessageDeliveryResponse(conversationId, MessageStatus.DELIVERED,
                    updatedIds, deliveredAt);
        }

        // Batch query to locate candidate messages in a single WAN round trip (DB-02 fix)
        Query findQuery = Query.query(Criteria.where("_id").in(sanitized)
                .and("conversationId").is(conversationId)
                .and("senderId").is(senderId)
                .and("deliveredAt").is(null));
        findQuery.fields().include("_id");

        List<Message> candidates = mongoTemplate.find(findQuery, Message.class);
        if (!candidates.isEmpty()) {
            List<String> matchedIds = candidates.stream().map(Message::getId).toList();
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("_id").in(matchedIds).and("deliveredAt").is(null)),
                    new Update().set("deliveredAt", deliveredAt),
                    Message.class);
            updatedIds.addAll(matchedIds);
        }

        if (!updatedIds.isEmpty()) {
            AfterCommit.run(() -> notifyReceipt(senderId, conversationId,
                    MessageStatus.DELIVERED, List.copyOf(updatedIds), deliveredAt));
        }

        return new MessageDeliveryResponse(conversationId, MessageStatus.DELIVERED,
                updatedIds, deliveredAt);
    }

    /**
     * Marks every message sent by the other participant in this conversation
     * as delivered and seen. Called by the existing conversation-read flow.
     *
     * @return the server timestamp used as the seen watermark, or null when
     *         there is no valid sender to update
     */
    public Instant markConversationSeen(String conversationId, String readerId, String senderId) {
        if (senderId == null || senderId.equals(readerId)) {
            return null;
        }

        Instant seenAt = Instant.now();

        // Delivery is filled first so that SEEN always implies DELIVERED, even
        // for messages that arrived while the recipient was offline. The
        // createdAt bound keeps the timestamp consistent with the emitted
        // watermark: a message created concurrently after this read is not
        // marked delivered before it existed.
        long modified = mongoTemplate.updateMulti(
                Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("senderId").is(senderId)
                        .and("createdAt").lte(seenAt)
                        .and("deliveredAt").is(null)),
                new Update().set("deliveredAt", seenAt),
                Message.class).getModifiedCount();

        modified += mongoTemplate.updateMulti(
                Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("senderId").is(senderId)
                        .and("createdAt").lte(seenAt)
                        // Conditional update keeps the timestamp monotonic.
                        .and("seenAt").is(null)),
                new Update().set("seenAt", seenAt),
                Message.class).getModifiedCount();

        if (modified > 0) {
            AfterCommit.run(() -> notifyReceipt(senderId, conversationId,
                    MessageStatus.SEEN, null, seenAt));
        }

        return seenAt;
    }

    /**
     * Pushes a receipt to every active STOMP session of the sender. Failures are
     * logged and swallowed: the persisted receipt is authoritative and a later
     * history fetch will reconcile the client.
     */
    private void notifyReceipt(String senderId, String conversationId, MessageStatus status,
                               List<String> messageIds, Instant occurredAt) {
        try {
            String username = userRepository.findById(senderId)
                    .map(User::getUsername)
                    .orElse(null);
            if (username == null) {
                return;
            }

            SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
            if (messagingTemplate == null) {
                log.warn("STOMP messaging template unavailable; skipped receipt push for user [{}] on conversation [{}]",
                        senderId, conversationId);
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    username,
                    RECEIPT_QUEUE,
                    new MessageReceiptUpdate(conversationId, status, messageIds, occurredAt));
        } catch (RuntimeException ex) {
            log.warn("Failed to push receipt for user [{}] on conversation [{}]: {}",
                    senderId, conversationId, ex.getMessage());
        }
    }

    private Set<String> sanitizeMessageIds(List<String> messageIds) {
        Set<String> sanitized = new LinkedHashSet<>();
        if (messageIds == null) {
            return sanitized;
        }
        for (String candidate : messageIds) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.matches(SAFE_MESSAGE_ID)) {
                sanitized.add(trimmed);
            }
        }
        if (sanitized.size() > MessageDeliveryRequest.MAX_MESSAGE_IDS) {
            throw new IllegalArgumentException(
                    "At most " + MessageDeliveryRequest.MAX_MESSAGE_IDS
                            + " message IDs may be acknowledged per request");
        }
        return sanitized;
    }
}
