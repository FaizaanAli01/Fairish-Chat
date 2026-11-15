package com.fairish.chat.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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

    /**
     * Legacy plaintext content. New messages never populate this field; it is
     * retained only so records written before encryption remain readable.
     */
    @Field(write = Field.Write.NON_NULL)
    private String content;

    /**
     * Base64 AES-256-GCM ciphertext (without the authentication tag).
     */
    @Field(write = Field.Write.NON_NULL)
    private String ciphertext;

    /**
     * Base64 96-bit random nonce used for this ciphertext.
     */
    @Field(write = Field.Write.NON_NULL)
    private String nonce;

    /**
     * Base64 128-bit GCM authentication tag.
     */
    @Field(write = Field.Write.NON_NULL)
    private String authTag;

    /**
     * Encryption format version. Absent or 0 means a legacy plaintext record;
     * 1 means AES-256-GCM with the fields above.
     */
    @Field(write = Field.Write.NON_NULL)
    private Integer encryptionVersion;

    /**
     * Identifier of the key used to encrypt this message, enabling rotation.
     */
    @Field(write = Field.Write.NON_NULL)
    private String keyId;

    @CreatedDate
    private Instant createdAt;

    /**
     * When the recipient's device acknowledged receipt. Null means the message
     * is still SENT. This is intentionally independent of {@link #seenAt}: a
     * seen message must also have been delivered, but delivery can happen while
     * the conversation is closed.
     */
    private Instant deliveredAt;

    /**
     * When the recipient opened the conversation and viewed this message. Null
     * when the message has not been seen yet.
     */
    private Instant seenAt;

    public Message() {
    }

    public Message(String id, String conversationId, String senderId, String content,
                   String ciphertext, String nonce, String authTag,
                   Integer encryptionVersion, String keyId, Instant createdAt,
                   Instant deliveredAt, Instant seenAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.authTag = authTag;
        this.encryptionVersion = encryptionVersion;
        this.keyId = keyId;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
        this.seenAt = seenAt;
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

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getAuthTag() {
        return authTag;
    }

    public void setAuthTag(String authTag) {
        this.authTag = authTag;
    }

    public Integer getEncryptionVersion() {
        return encryptionVersion;
    }

    public void setEncryptionVersion(Integer encryptionVersion) {
        this.encryptionVersion = encryptionVersion;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
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
        private String ciphertext;
        private String nonce;
        private String authTag;
        private Integer encryptionVersion;
        private String keyId;
        private Instant createdAt;
        private Instant deliveredAt;
        private Instant seenAt;

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

        public MessageBuilder ciphertext(String ciphertext) {
            this.ciphertext = ciphertext;
            return this;
        }

        public MessageBuilder nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public MessageBuilder authTag(String authTag) {
            this.authTag = authTag;
            return this;
        }

        public MessageBuilder encryptionVersion(Integer encryptionVersion) {
            this.encryptionVersion = encryptionVersion;
            return this;
        }

        public MessageBuilder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public MessageBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MessageBuilder deliveredAt(Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public MessageBuilder seenAt(Instant seenAt) {
            this.seenAt = seenAt;
            return this;
        }

        public Message build() {
            return new Message(id, conversationId, senderId, content, ciphertext, nonce,
                    authTag, encryptionVersion, keyId, createdAt, deliveredAt, seenAt);
        }
    }
}
