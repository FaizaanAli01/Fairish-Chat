package com.fairish.chat.model;

/**
 * Lifecycle of a contact request between two users.
 *
 * <p>Exactly one {@link ContactRequest} document exists per user pair (keyed by
 * a deterministic pair key), so a pair is never both "pending" and "accepted"
 * at the same time.</p>
 */
public enum ContactStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
