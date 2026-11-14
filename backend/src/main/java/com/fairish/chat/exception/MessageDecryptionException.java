package com.fairish.chat.exception;

/**
 * Raised when an encrypted message cannot be authenticated or decoded.
 *
 * <p>Messages intentionally contain no ciphertext, nonce, tag, key material or
 * cryptographic library details so the exception is safe to log and is never
 * exposed verbatim to API clients.</p>
 */
public class MessageDecryptionException extends RuntimeException {

    public MessageDecryptionException(String message) {
        super(message);
    }

    public MessageDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
