package com.fairish.chat.exception;

/**
 * Raised when an uploaded avatar is missing, too large, of an unsupported
 * format, corrupt, or otherwise fails validation/processing.
 */
public class InvalidAvatarException extends RuntimeException {

    public InvalidAvatarException(String message) {
        super(message);
    }

    public InvalidAvatarException(String message, Throwable cause) {
        super(message, cause);
    }
}
