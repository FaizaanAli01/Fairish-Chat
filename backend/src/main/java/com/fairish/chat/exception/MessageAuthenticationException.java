package com.fairish.chat.exception;

/**
 * Raised when an encrypted message fails AES-GCM authentication.
 *
 * <p>This is distinct from structural corruption (malformed Base64, invalid
 * nonce/tag lengths, unsupported versions). Authentication failures must fail
 * closed because they can indicate tampering, so they are not degraded to a
 * placeholder.</p>
 */
public class MessageAuthenticationException extends MessageDecryptionException {

    public MessageAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
