package com.fairish.chat.service;

import com.fairish.chat.dto.UnreadCountUpdate;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
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

/**
 * Atomic per-user unread state for conversations.
 *
 * <p>The unread counter lives on the conversation document and is updated with
 * a single MongoDB {@code $inc}, never by reading and saving a whole document.
 * This prevents concurrent messages from overwriting each other's increments.
 * The message summary and the recipient counter are updated in the same atomic
 * operation.</p>
 */
@Service
public class ConversationUnreadService {

    private static final Logger log = LoggerFactory.getLogger(ConversationUnreadService.class);
    private static final String UNREAD_FIELD_PREFIX = "unreadCounts.";

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    /**
     * The broker-backed {@link SimpMessagingTemplate} is resolved lazily through
     * an {@link ObjectProvider}. Eagerly injecting it here would create the
     * cycle
     * {@code WebSocketConfig -> WebSocketAuthInterceptor -> ConversationService
     * -> ConversationUnreadService -> SimpMessagingTemplate ->
     * DelegatingWebSocketMessageBrokerConfiguration -> WebSocketConfig}.
     * Unread state has no reason to exist only after the STOMP broker starts,
     * so the lookup is deferred until a notification is actually sent.
     */
    public ConversationUnreadService(MongoTemplate mongoTemplate,
                                     UserRepository userRepository,
                                     ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    /**
     * Atomically updates the conversation summary and increments only the
     * recipient's unread counter. Returns the updated conversation, or null if
     * the conversation disappeared concurrently.
     */
    public Conversation recordMessageAndIncrementUnread(String conversationId,
                                                        String senderId,
                                                        String recipientId,
                                                        String content,
                                                        Instant sentAt) {
        Update update = new Update()
                .set("lastMessageText", content)
                .set("lastMessageSenderId", senderId)
                .set("lastMessageAt", sentAt)
                .set("updatedAt", sentAt)
                .inc(unreadField(recipientId), 1);

        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(conversationId)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);
    }

    /**
     * Resets one participant's unread counter to zero.
     */
    public Conversation resetUnread(String conversationId, String userId) {
        Update update = new Update()
                .set(unreadField(userId), 0);

        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(conversationId)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);
    }

    /**
     * Pushes an authoritative count to every active STOMP session of one user.
     * Notification failures must never fail the originating message/read
     * operation, so they are logged and swallowed.
     *
     * <p>The STOMP template is resolved here, not at construction time, which
     * keeps the WebSocket broker configuration out of this bean's eager
     * dependency graph.</p>
     */
    public void notifyUnreadCount(String userId, String conversationId, int unreadCount) {
        try {
            String username = userRepository.findById(userId)
                    .map(User::getUsername)
                    .orElse(null);
            if (username == null) {
                return;
            }

            SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
            if (messagingTemplate == null) {
                log.warn("STOMP messaging template unavailable; skipped unread push for user [{}] on conversation [{}]",
                        userId, conversationId);
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/unread",
                    new UnreadCountUpdate(conversationId, unreadCount));
        } catch (RuntimeException ex) {
            log.warn("Failed to push unread count for user [{}] on conversation [{}]: {}",
                    userId, conversationId, ex.getMessage());
        }
    }

    private String unreadField(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid user ID for unread state");
        }
        return UNREAD_FIELD_PREFIX + userId;
    }
}
