package com.fairish.chat.dto;

import java.time.Instant;

/**
 * An accepted contact of the authenticated user.
 */
public class ContactResponse {

    private PublicUserResponse user;
    private Instant acceptedAt;

    public ContactResponse() {
    }

    public ContactResponse(PublicUserResponse user, Instant acceptedAt) {
        this.user = user;
        this.acceptedAt = acceptedAt;
    }

    public static ContactResponseBuilder builder() {
        return new ContactResponseBuilder();
    }

    public PublicUserResponse getUser() {
        return user;
    }

    public void setUser(PublicUserResponse user) {
        this.user = user;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public static class ContactResponseBuilder {
        private PublicUserResponse user;
        private Instant acceptedAt;

        ContactResponseBuilder() {
        }

        public ContactResponseBuilder user(PublicUserResponse user) {
            this.user = user;
            return this;
        }

        public ContactResponseBuilder acceptedAt(Instant acceptedAt) {
            this.acceptedAt = acceptedAt;
            return this;
        }

        public ContactResponse build() {
            return new ContactResponse(user, acceptedAt);
        }
    }
}
