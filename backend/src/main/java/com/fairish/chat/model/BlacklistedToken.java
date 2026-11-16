package com.fairish.chat.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A revoked JWT, persisted so logout survives an application restart and is
 * visible to every application instance.
 *
 * <p>Only a SHA-256 digest of the raw token is stored. The raw token is never
 * written to MongoDB, so a database read cannot be used to replay a session.
 * The document is automatically removed by a TTL index once the underlying JWT
 * would have expired anyway.</p>
 */
@Document(collection = "blacklisted_tokens")
public class BlacklistedToken {

    /** SHA-256 hex digest of the raw JWT. */
    @Id
    private String tokenHash;

    /** When the underlying JWT expires; the TTL monitor removes the document after this. */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    public BlacklistedToken() {
    }

    public BlacklistedToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
