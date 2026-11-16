package com.fairish.chat.exception;

/**
 * Raised when a validated JWT cannot be stored in the revocation list during
 * logout. The original cause is logged server-side only; clients receive a
 * generic service-unavailable response.
 */
public class TokenBlacklistException extends RuntimeException {

    public TokenBlacklistException(String message, Throwable cause) {
        super(message, cause);
    }
}
