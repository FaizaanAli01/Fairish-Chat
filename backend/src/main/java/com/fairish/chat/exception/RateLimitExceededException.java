package com.fairish.chat.exception;

/**
 * Raised when an account-level or endpoint-level rate limit is exceeded.
 * The message is safe to return to the client and never contains an account
 * identifier or client IP address.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
