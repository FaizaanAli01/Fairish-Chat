package com.fairish.chat.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationResponse {

    private String id;
    private List<String> participantIds = new ArrayList<>();
    private PublicUserResponse otherUser;
    private String lastMessageText;
    private String lastMessageSenderId;
    private Instant lastMessageAt;
    private Instant createdAt;
    private Instant updatedAt;

    public ConversationResponse() {
    }

    public ConversationResponse(String id, List<String> participantIds, PublicUserResponse otherUser,
                                String lastMessageText, String lastMessageSenderId, Instant lastMessageAt,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.participantIds = participantIds != null ? participantIds : new ArrayList<>();
        this.otherUser = otherUser;
        this.lastMessageText = lastMessageText;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ConversationResponseBuilder builder() {
        return new ConversationResponseBuilder();
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

    public PublicUserResponse getOtherUser() {
        return otherUser;
    }

    public void setOtherUser(PublicUserResponse otherUser) {
        this.otherUser = otherUser;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
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

    public static class ConversationResponseBuilder {
        private String id;
        private List<String> participantIds = new ArrayList<>();
        private PublicUserResponse otherUser;
        private String lastMessageText;
        private String lastMessageSenderId;
        private Instant lastMessageAt;
        private Instant createdAt;
        private Instant updatedAt;

        public ConversationResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ConversationResponseBuilder participantIds(List<String> participantIds) {
            this.participantIds = participantIds;
            return this;
        }

        public ConversationResponseBuilder otherUser(PublicUserResponse otherUser) {
            this.otherUser = otherUser;
            return this;
        }

        public ConversationResponseBuilder lastMessageText(String lastMessageText) {
            this.lastMessageText = lastMessageText;
            return this;
        }

        public ConversationResponseBuilder lastMessageSenderId(String lastMessageSenderId) {
            this.lastMessageSenderId = lastMessageSenderId;
            return this;
        }

        public ConversationResponseBuilder lastMessageAt(Instant lastMessageAt) {
            this.lastMessageAt = lastMessageAt;
            return this;
        }

        public ConversationResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ConversationResponseBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ConversationResponse build() {
            return new ConversationResponse(id, participantIds, otherUser,
                    lastMessageText, lastMessageSenderId, lastMessageAt, createdAt, updatedAt);
        }
    }
}
