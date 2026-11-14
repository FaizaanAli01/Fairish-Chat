package com.fairish.chat.encryption;

/**
 * Result of one AES-256-GCM encryption operation.
 *
 * <p>The authentication tag returned by the JCE is split from the ciphertext so
 * it can be stored as an explicit field. All binary values are encoded as
 * standard Base64. {@code encryptionVersion} describes the on-disk format and
 * {@code keyId} identifies the key that was used, which is required for future
 * key rotation.</p>
 */
public record EncryptedPayload(
        String ciphertext,
        String nonce,
        String authTag,
        int encryptionVersion,
        String keyId) {
}
