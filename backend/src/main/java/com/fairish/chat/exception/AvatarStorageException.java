package com.fairish.chat.exception;

/**
 * Raised when the avatar object store is unreachable or rejects an operation.
 * The original cause is logged server-side only; clients receive a generic
 * service-unavailable response.
 */
public class AvatarStorageException extends RuntimeException {

    public AvatarStorageException(String message) {
        super(message);
    }

    public AvatarStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
