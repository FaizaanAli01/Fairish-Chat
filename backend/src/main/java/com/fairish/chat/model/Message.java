package com.fairish.chat.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "conv_created_asc_idx", def = "{'conversationId': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "conv_created_desc_idx", def = "{'conversationId': 1, 'createdAt': -1}")
})
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String senderId;

    private String content;

    @CreatedDate
    private Instant createdAt;

    public Message() {
    }

    public Message(String id, String conversationId, String senderId, String content, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    // Getters and Setters

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static class MessageBuilder {
        private String id;
        private String conversationId;
        private String senderId;
        private String content;
        private Instant createdAt;

        public MessageBuilder id(String id) {
            this.id = id;
            return this;
        }

        public MessageBuilder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public MessageBuilder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public MessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        public MessageBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Message build() {
            return new Message(id, conversationId, senderId, content, createdAt);
        }
    }
}
