package com.fairish.chat.dto;

import com.fairish.chat.model.ContactRequest;
import com.fairish.chat.model.ContactStatus;
import com.fairish.chat.model.User;

import java.time.Instant;

/**
 * Contact request projection. Both parties are exposed only as
 * {@link PublicUserResponse}, so no email address ever leaves the API here.
 */
public class ContactRequestResponse {

    private String id;
    private PublicUserResponse requester;
    private PublicUserResponse recipient;
    private ContactStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant respondedAt;

    public ContactRequestResponse() {
    }

    public ContactRequestResponse(String id, PublicUserResponse requester, PublicUserResponse recipient,
                                  ContactStatus status, Instant createdAt, Instant updatedAt, Instant respondedAt) {
        this.id = id;
        this.requester = requester;
        this.recipient = recipient;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.respondedAt = respondedAt;
    }

    public static ContactRequestResponse from(ContactRequest request, User requester, User recipient) {
        return ContactRequestResponse.builder()
                .id(request.getId())
                .requester(PublicUserResponse.fromUser(requester))
                .recipient(PublicUserResponse.fromUser(recipient))
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .respondedAt(request.getRespondedAt())
                .build();
    }

    public static ContactRequestResponseBuilder builder() {
        return new ContactRequestResponseBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PublicUserResponse getRequester() {
        return requester;
    }

    public void setRequester(PublicUserResponse requester) {
        this.requester = requester;
    }

    public PublicUserResponse getRecipient() {
        return recipient;
    }

    public void setRecipient(PublicUserResponse recipient) {
        this.recipient = recipient;
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

    public static class ContactRequestResponseBuilder {
        private String id;
        private PublicUserResponse requester;
        private PublicUserResponse recipient;
        private ContactStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant respondedAt;

        ContactRequestResponseBuilder() {
        }

        public ContactRequestResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ContactRequestResponseBuilder requester(PublicUserResponse requester) {
            this.requester = requester;
            return this;
        }

        public ContactRequestResponseBuilder recipient(PublicUserResponse recipient) {
            this.recipient = recipient;
            return this;
        }

        public ContactRequestResponseBuilder status(ContactStatus status) {
            this.status = status;
            return this;
        }

        public ContactRequestResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ContactRequestResponseBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ContactRequestResponseBuilder respondedAt(Instant respondedAt) {
            this.respondedAt = respondedAt;
            return this;
        }

        public ContactRequestResponse build() {
            return new ContactRequestResponse(id, requester, recipient, status,
                    createdAt, updatedAt, respondedAt);
        }
    }
}
