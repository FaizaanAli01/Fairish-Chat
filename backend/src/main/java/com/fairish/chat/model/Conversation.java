package com.fairish.chat.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Document(collection = "conversations")
@CompoundIndexes({
        @CompoundIndex(name = "participants_idx", def = "{'participantIds': 1}"),
        @CompoundIndex(name = "updated_at_idx", def = "{'updatedAt': -1}")
})
public class Conversation {

    @Id
    private String id;

    /**
     * List of participant user IDs. Always contains exactly two normalized user IDs.
     */
    private List<String> participantIds = new ArrayList<>();

    /**
     * Deterministic key (e.g. "userId1:userId2" with sorted IDs) with a unique index
     * to safely prevent duplicate conversations between the same two users.
     */
    @Indexed(unique = true)
    private String participantKey;

    /**
     * Legacy plaintext preview. New messages never update this field; it is
     * retained only for conversations whose latest message predates encryption.
     */
    @Field(write = Field.Write.NON_NULL)
    private String lastMessageText;
    private String lastMessageSenderId;
    private Instant lastMessageAt;

    /**
     * AES-256-GCM preview of the latest message. Stored in place of
     * {@link #lastMessageText} for every new message.
     */
    @Field(write = Field.Write.NON_NULL)
    private String lastMessageCiphertext;

    @Field(write = Field.Write.NON_NULL)
    private String lastMessageNonce;

    @Field(write = Field.Write.NON_NULL)
    private String lastMessageAuthTag;

    @Field(write = Field.Write.NON_NULL)
    private Integer lastMessageEncryptionVersion;

    @Field(write = Field.Write.NON_NULL)
    private String lastMessageKeyId;

    /**
     * Unread message count per participant user ID. Only the recipient of a
     * message is incremented; opening the conversation resets the current
     * user's entry. Existing conversations without this field read as zero.
     */
    private Map<String, Integer> unreadCounts = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Conversation() {
    }

    public Conversation(String id, List<String> participantIds, String participantKey,
                        String lastMessageText, String lastMessageSenderId, Instant lastMessageAt,
                        String lastMessageCiphertext, String lastMessageNonce, String lastMessageAuthTag,
                        Integer lastMessageEncryptionVersion, String lastMessageKeyId,
                        Map<String, Integer> unreadCounts, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.participantIds = participantIds != null ? participantIds : new ArrayList<>();
        this.participantKey = participantKey;
        this.lastMessageText = lastMessageText;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageAt = lastMessageAt;
        this.lastMessageCiphertext = lastMessageCiphertext;
        this.lastMessageNonce = lastMessageNonce;
        this.lastMessageAuthTag = lastMessageAuthTag;
        this.lastMessageEncryptionVersion = lastMessageEncryptionVersion;
        this.lastMessageKeyId = lastMessageKeyId;
        this.unreadCounts = unreadCounts != null ? unreadCounts : new HashMap<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ConversationBuilder builder() {
        return new ConversationBuilder();
    }

    /**
     * Normalizes two user IDs into a sorted list and deterministic participantKey.
     */
    public static String computeParticipantKey(String user1Id, String user2Id) {
        if (user1Id == null || user2Id == null) {
            throw new IllegalArgumentException("User IDs cannot be null");
        }
        if (user1Id.equals(user2Id)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }
        return user1Id.compareTo(user2Id) < 0
                ? user1Id + ":" + user2Id
                : user2Id + ":" + user1Id;
    }

    public static List<String> normalizeParticipants(String user1Id, String user2Id) {
        if (user1Id == null || user2Id == null) {
            throw new IllegalArgumentException("User IDs cannot be null");
        }
        if (user1Id.equals(user2Id)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }
        List<String> list = new ArrayList<>(List.of(user1Id, user2Id));
        Collections.sort(list);
        return list;
    }

    public boolean hasParticipant(String userId) {
        return userId != null && participantIds != null && participantIds.contains(userId);
    }

    public String getOtherParticipantId(String currentUserId) {
        if (participantIds == null || participantIds.size() != 2) {
            return null;
        }
        return participantIds.get(0).equals(currentUserId)
                ? participantIds.get(1)
                : participantIds.get(0);
    }

    /**
     * Returns the persisted unread count for one participant. Missing entries
     * (including conversations created before unread support) are treated as 0.
     */
    public int getUnreadCount(String userId) {
        if (userId == null || unreadCounts == null) {
            return 0;
        }
        Integer count = unreadCounts.get(userId);
        return count != null && count > 0 ? count : 0;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public String getParticipantKey() {
        return participantKey;
    }

    public void setParticipantKey(String participantKey) {
        this.participantKey = participantKey;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public String getLastMessageCiphertext() {
        return lastMessageCiphertext;
    }

    public void setLastMessageCiphertext(String lastMessageCiphertext) {
        this.lastMessageCiphertext = lastMessageCiphertext;
    }

    public String getLastMessageNonce() {
        return lastMessageNonce;
    }

    public void setLastMessageNonce(String lastMessageNonce) {
        this.lastMessageNonce = lastMessageNonce;
    }

    public String getLastMessageAuthTag() {
        return lastMessageAuthTag;
    }

    public void setLastMessageAuthTag(String lastMessageAuthTag) {
        this.lastMessageAuthTag = lastMessageAuthTag;
    }

    public Integer getLastMessageEncryptionVersion() {
        return lastMessageEncryptionVersion;
    }

    public void setLastMessageEncryptionVersion(Integer lastMessageEncryptionVersion) {
        this.lastMessageEncryptionVersion = lastMessageEncryptionVersion;
    }

    public String getLastMessageKeyId() {
        return lastMessageKeyId;
    }

    public void setLastMessageKeyId(String lastMessageKeyId) {
        this.lastMessageKeyId = lastMessageKeyId;
    }

    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Map<String, Integer> getUnreadCounts() {
        return unreadCounts;
    }

    public void setUnreadCounts(Map<String, Integer> unreadCounts) {
        this.unreadCounts = unreadCounts != null ? unreadCounts : new HashMap<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return Objects.equals(id, that.id) && Objects.equals(participantKey, that.participantKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, participantKey);
    }

    public static class ConversationBuilder {
        private String id;
        private List<String> participantIds = new ArrayList<>();
        private String participantKey;
        private String lastMessageText;
        private String lastMessageSenderId;
        private Instant lastMessageAt;
        private String lastMessageCiphertext;
        private String lastMessageNonce;
        private String lastMessageAuthTag;
        private Integer lastMessageEncryptionVersion;
        private String lastMessageKeyId;
        private Map<String, Integer> unreadCounts = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public ConversationBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ConversationBuilder participantIds(List<String> participantIds) {
            this.participantIds = participantIds;
            return this;
        }

        public ConversationBuilder participantKey(String participantKey) {
            this.participantKey = participantKey;
            return this;
        }

        public ConversationBuilder lastMessageText(String lastMessageText) {
            this.lastMessageText = lastMessageText;
            return this;
        }

        public ConversationBuilder lastMessageSenderId(String lastMessageSenderId) {
            this.lastMessageSenderId = lastMessageSenderId;
            return this;
        }

        public ConversationBuilder lastMessageAt(Instant lastMessageAt) {
            this.lastMessageAt = lastMessageAt;
            return this;
        }

        public ConversationBuilder lastMessageCiphertext(String lastMessageCiphertext) {
            this.lastMessageCiphertext = lastMessageCiphertext;
            return this;
        }

        public ConversationBuilder lastMessageNonce(String lastMessageNonce) {
            this.lastMessageNonce = lastMessageNonce;
            return this;
        }

        public ConversationBuilder lastMessageAuthTag(String lastMessageAuthTag) {
            this.lastMessageAuthTag = lastMessageAuthTag;
            return this;
        }

        public ConversationBuilder lastMessageEncryptionVersion(Integer lastMessageEncryptionVersion) {
            this.lastMessageEncryptionVersion = lastMessageEncryptionVersion;
            return this;
        }

        public ConversationBuilder lastMessageKeyId(String lastMessageKeyId) {
            this.lastMessageKeyId = lastMessageKeyId;
            return this;
        }

        public ConversationBuilder unreadCounts(Map<String, Integer> unreadCounts) {
            this.unreadCounts = unreadCounts;
            return this;
        }

        public ConversationBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ConversationBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Conversation build() {
            return new Conversation(id, participantIds, participantKey,
                    lastMessageText, lastMessageSenderId, lastMessageAt,
                    lastMessageCiphertext, lastMessageNonce, lastMessageAuthTag,
                    lastMessageEncryptionVersion, lastMessageKeyId,
                    unreadCounts, createdAt, updatedAt);
        }
    }
}
