package com.fairish.chat.dto;

import com.fairish.chat.model.Message;

import java.time.Instant;

public class MessageResponse {

    private String id;
    private String conversationId;
    private String senderId;
    private String content;
    private Instant createdAt;

    public MessageResponse() {
    }

    public MessageResponse(String id, String conversationId, String senderId, String content, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static MessageResponse fromMessage(Message message) {
        if (message == null) {
            return null;
        }
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public static MessageResponseBuilder builder() {
        return new MessageResponseBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static class MessageResponseBuilder {
        private String id;
        private String conversationId;
        private String senderId;
        private String content;
        private Instant createdAt;

        public MessageResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public MessageResponseBuilder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public MessageResponseBuilder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public MessageResponseBuilder content(String content) {
            this.content = content;
            return this;
        }

        public MessageResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MessageResponse build() {
            return new MessageResponse(id, conversationId, senderId, content, createdAt);
        }
    }
}
