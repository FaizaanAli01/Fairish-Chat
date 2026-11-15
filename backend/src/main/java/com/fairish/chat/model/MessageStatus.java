package com.fairish.chat.model;

/**
 * Delivery state of a message, derived from its receipt timestamps.
 *
 * <p>The status is never persisted as a separate field: {@code deliveredAt}
 * and {@code seenAt} are the source of truth, which keeps receipt updates
 * idempotent and monotonic.</p>
 */
public enum MessageStatus {
    SENT,
    DELIVERED,
    SEEN;

    public static MessageStatus fromMessage(Message message) {
        if (message == null) {
            return SENT;
        }
        if (message.getSeenAt() != null) {
            return SEEN;
        }
        if (message.getDeliveredAt() != null) {
            return DELIVERED;
        }
        return SENT;
    }
}
