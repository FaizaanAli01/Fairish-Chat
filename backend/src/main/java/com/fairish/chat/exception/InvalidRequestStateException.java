package com.fairish.chat.exception;

/**
 * Thrown when an operation is not valid for the current state of a resource,
 * for example accepting a contact request that is no longer pending.
 */
public class InvalidRequestStateException extends RuntimeException {
    public InvalidRequestStateException(String message) {
        super(message);
    }
}
