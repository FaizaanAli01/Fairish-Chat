package com.fairish.chat.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * A contact request / accepted contact relationship between two users.
 *
 * <p>There is a single document per unordered user pair, identified by
 * {@link #pairKey}. An {@link ContactStatus#ACCEPTED} document is what makes
 * two users contacts; it is the only source of truth for contact-based access
 * checks (for example, avatar visibility).</p>
 */
@Document(collection = "contact_requests")
@CompoundIndexes({
        @CompoundIndex(name = "contact_pair_key_unique_idx", def = "{'pairKey': 1}", unique = true),
        @CompoundIndex(name = "contact_recipient_status_idx", def = "{'recipientId': 1, 'status': 1}"),
        @CompoundIndex(name = "contact_requester_status_idx", def = "{'requesterId': 1, 'status': 1}")
})
public class ContactRequest {

    @Id
    private String id;

    /**
     * Deterministic key for the unordered user pair ("lowerId:higherId").
     */
    private String pairKey;

    private String requesterId;
    private String recipientId;
    private ContactStatus status = ContactStatus.PENDING;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant respondedAt;

    public ContactRequest() {
    }

    public ContactRequest(String id, String pairKey, String requesterId, String recipientId,
                          ContactStatus status, Instant createdAt, Instant updatedAt, Instant respondedAt) {
        this.id = id;
        this.pairKey = pairKey;
        this.requesterId = requesterId;
        this.recipientId = recipientId;
        this.status = status != null ? status : ContactStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.respondedAt = respondedAt;
    }

    public static ContactRequestBuilder builder() {
        return new ContactRequestBuilder();
    }

    /**
     * Normalizes two user IDs into an order-independent pair key.
     */
    public static String computePairKey(String user1Id, String user2Id) {
        if (user1Id == null || user2Id == null) {
            throw new IllegalArgumentException("User IDs cannot be null");
        }
        if (user1Id.equals(user2Id)) {
            throw new IllegalArgumentException("A user cannot be a contact of themselves");
        }
        return user1Id.compareTo(user2Id) < 0
                ? user1Id + ":" + user2Id
                : user2Id + ":" + user1Id;
    }

    public boolean hasParticipant(String userId) {
        return userId != null
                && (userId.equals(requesterId) || userId.equals(recipientId));
    }

    public String getOtherParticipantId(String currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        if (currentUserId.equals(requesterId)) {
            return recipientId;
        }
        if (currentUserId.equals(recipientId)) {
            return requesterId;
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPairKey() {
        return pairKey;
    }

    public void setPairKey(String pairKey) {
        this.pairKey = pairKey;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public void setStatus(ContactStatus status) {
        this.status = status;
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

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactRequest that = (ContactRequest) o;
        return Objects.equals(id, that.id) && Objects.equals(pairKey, that.pairKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pairKey);
    }

    public static class ContactRequestBuilder {
        private String id;
        private String pairKey;
        private String requesterId;
        private String recipientId;
        private ContactStatus status = ContactStatus.PENDING;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant respondedAt;

        ContactRequestBuilder() {
        }

        public ContactRequestBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ContactRequestBuilder pairKey(String pairKey) {
            this.pairKey = pairKey;
            return this;
        }

        public ContactRequestBuilder requesterId(String requesterId) {
            this.requesterId = requesterId;
            return this;
        }

        public ContactRequestBuilder recipientId(String recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public ContactRequestBuilder status(ContactStatus status) {
            this.status = status;
            return this;
        }

        public ContactRequestBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ContactRequestBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ContactRequestBuilder respondedAt(Instant respondedAt) {
            this.respondedAt = respondedAt;
            return this;
        }

        public ContactRequest build() {
            return new ContactRequest(id, pairKey, requesterId, recipientId,
                    status, createdAt, updatedAt, respondedAt);
        }
    }
}
