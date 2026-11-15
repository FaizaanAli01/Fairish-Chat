package com.fairish.chat.dto;

import com.fairish.chat.model.Message;
import com.fairish.chat.model.MessageStatus;

import java.time.Instant;

public class MessageResponse {

    private String id;
    private String conversationId;
    private String senderId;
    private String content;
    private Instant createdAt;
    private Instant deliveredAt;
    private Instant seenAt;
    private MessageStatus status;

    public MessageResponse() {
    }

    public MessageResponse(String id, String conversationId, String senderId, String content,
                           Instant createdAt, Instant deliveredAt, Instant seenAt, MessageStatus status) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
        this.seenAt = seenAt;
        this.status = status;
    }

    /**
     * Builds the client-facing response. The caller must provide decrypted
     * plaintext; encrypted model fields are never copied into API responses.
     */
    public static MessageResponse fromMessage(Message message, String decryptedContent) {
        if (message == null) {
            return null;
        }
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(decryptedContent)
                .createdAt(message.getCreatedAt())
                .deliveredAt(message.getDeliveredAt())
                .seenAt(message.getSeenAt())
                .status(MessageStatus.fromMessage(message))
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

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(Instant seenAt) {
        this.seenAt = seenAt;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public static class MessageResponseBuilder {
        private String id;
        private String conversationId;
        private String senderId;
        private String content;
        private Instant createdAt;
        private Instant deliveredAt;
        private Instant seenAt;
        private MessageStatus status;

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

        public MessageResponseBuilder deliveredAt(Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public MessageResponseBuilder seenAt(Instant seenAt) {
            this.seenAt = seenAt;
            return this;
        }

        public MessageResponseBuilder status(MessageStatus status) {
            this.status = status;
            return this;
        }

        public MessageResponse build() {
            return new MessageResponse(id, conversationId, senderId, content, createdAt,
                    deliveredAt, seenAt, status);
        }
    }
}
