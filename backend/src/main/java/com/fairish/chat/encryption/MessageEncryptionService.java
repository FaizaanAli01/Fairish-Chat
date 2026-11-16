package com.fairish.chat.encryption;

import com.fairish.chat.exception.MessageAuthenticationException;
import com.fairish.chat.exception.MessageDecryptionException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side AES-256-GCM encryption for message content at rest.
 *
 * <p>Every call to {@link #encrypt(String)} generates a fresh 96-bit nonce from
 * a {@link SecureRandom}. Nonces are never derived from message data and are
 * never reused for another encryption operation. The 128-bit GCM
 * authentication tag is stored separately from the ciphertext.</p>
 *
 * <p>The key never leaves this process. It is decoded from Base64
 * configuration, must be exactly 32 bytes, and is only referenced through
 * {@link SecretKey} instances. Key identifiers are stored alongside the
 * ciphertext so that old messages remain readable after a key rotation.</p>
 */
@Service
public class MessageEncryptionService {

    public static final int CURRENT_ENCRYPTION_VERSION = 1;
    public static final String PREVIEW_UNAVAILABLE = "[Message unavailable]";

    static final int KEY_LENGTH_BYTES = 32;
    static final int NONCE_LENGTH_BYTES = 12;
    static final int AUTH_TAG_LENGTH_BITS = 128;
    static final int AUTH_TAG_LENGTH_BYTES = AUTH_TAG_LENGTH_BITS / 8;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(MessageEncryptionService.class);

    private final boolean enabled;
    private final String activeKeyId;
    private final Map<String, SecretKey> keyRing;
    private final SecureRandom secureRandom;

    public MessageEncryptionService(MessageEncryptionProperties properties) {
        this.enabled = properties.isEnabled();
        this.secureRandom = new SecureRandom();

        if (!enabled) {
            this.activeKeyId = null;
            this.keyRing = Collections.emptyMap();
            log.warn("Message encryption is DISABLED (app.encryption.enabled=false). "
                    + "New messages cannot be persisted until encryption is enabled with a valid key.");
            return;
        }

        String configuredActiveKeyId = properties.getActiveKeyId();
        validateKeyId(configuredActiveKeyId, "app.encryption.active-key-id");

        Map<String, SecretKey> parsedKeys = new LinkedHashMap<>();
        parsePreviousKeys(properties.getPreviousKeys(), parsedKeys);
        if (parsedKeys.containsKey(configuredActiveKeyId)) {
            throw new IllegalStateException(
                    "The active encryption key ID must differ from every previous key ID.");
        }
        parsedKeys.put(configuredActiveKeyId, parseKey(
                properties.getKey(), "MESSAGE_ENCRYPTION_KEY"));

        this.activeKeyId = configuredActiveKeyId;
        this.keyRing = Collections.unmodifiableMap(parsedKeys);

        log.info("Message encryption enabled: activeKeyId={}, readableKeyIds={}",
                activeKeyId, keyRing.keySet());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    /**
     * Encrypts plaintext with the active key and a fresh random nonce.
     */
    public EncryptedPayload encrypt(String plaintext) {
        if (!enabled) {
            throw new IllegalStateException(
                    "Message encryption is disabled. Configure MESSAGE_ENCRYPTION_KEY before sending messages.");
        }
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext message content is required");
        }

        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyRing.get(activeKeyId),
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            int ciphertextLength = sealed.length - AUTH_TAG_LENGTH_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(sealed, 0, ciphertextLength);
            byte[] authTag = Arrays.copyOfRange(sealed, ciphertextLength, sealed.length);

            return new EncryptedPayload(
                    encode(ciphertext),
                    encode(nonce),
                    encode(authTag),
                    CURRENT_ENCRYPTION_VERSION,
                    activeKeyId);
        } catch (GeneralSecurityException ex) {
            // Never include key material or plaintext in the exception.
            throw new IllegalStateException("Message encryption failed", ex);
        }
    }

    /**
     * Decrypts a persisted message, transparently supporting legacy plaintext
     * records that predate encryption.
     *
     * @throws MessageDecryptionException when an encrypted record is malformed,
     *                                    uses an unknown version/key, or fails
     *                                    authentication
     */
    public String decrypt(Message message) {
        if (message == null) {
            throw new MessageDecryptionException("Message is missing");
        }

        Integer version = message.getEncryptionVersion();
        if (isLegacy(version)) {
            if (message.getContent() == null) {
                throw new MessageDecryptionException("Message has no readable content");
            }
            return message.getContent();
        }

        return decrypt(version, message.getKeyId(), message.getCiphertext(),
                message.getNonce(), message.getAuthTag());
    }

    /**
     * Decrypts a conversation preview. A corrupt or undecryptable preview must
     * not hide the whole conversation list, so it is replaced by a neutral
     * placeholder rather than exposing ciphertext.
     */
    public String decryptConversationPreview(Conversation conversation) {
        if (conversation == null) {
            return null;
        }

        Integer version = conversation.getLastMessageEncryptionVersion();
        if (isLegacy(version)) {
            return conversation.getLastMessageText();
        }

        try {
            return decrypt(version, conversation.getLastMessageKeyId(),
                    conversation.getLastMessageCiphertext(),
                    conversation.getLastMessageNonce(),
                    conversation.getLastMessageAuthTag());
        } catch (MessageDecryptionException ex) {
            log.warn("Unable to decrypt the latest-message preview for conversation [{}]; "
                    + "returning a placeholder", conversation.getId());
            return PREVIEW_UNAVAILABLE;
        }
    }

    /**
     * Decrypts an in-memory payload, used by migration tooling and tests.
     */
    public String decrypt(EncryptedPayload payload) {
        if (payload == null) {
            throw new MessageDecryptionException("Encrypted payload is missing");
        }
        return decrypt(payload.encryptionVersion(), payload.keyId(), payload.ciphertext(),
                payload.nonce(), payload.authTag());
    }

    private String decrypt(Integer version, String keyId, String ciphertext,
                           String nonce, String authTag) {
        if (version == null || version != CURRENT_ENCRYPTION_VERSION) {
            throw new MessageDecryptionException("Unsupported message encryption version");
        }
        if (!StringUtils.hasText(keyId) || !StringUtils.hasText(ciphertext)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(authTag)) {
            throw new MessageDecryptionException("Encrypted message record is incomplete");
        }

        SecretKey key = keyRing.get(keyId);
        if (key == null) {
            throw new MessageDecryptionException("No decryption key is available for this message");
        }

        byte[] ciphertextBytes;
        byte[] nonceBytes;
        byte[] authTagBytes;
        try {
            ciphertextBytes = decode(ciphertext);
            nonceBytes = decode(nonce);
            authTagBytes = decode(authTag);
        } catch (IllegalArgumentException ex) {
            throw new MessageDecryptionException("Encrypted message record is malformed", ex);
        }

        if (nonceBytes.length != NONCE_LENGTH_BYTES) {
            throw new MessageDecryptionException("Encrypted message nonce has an invalid length");
        }
        if (authTagBytes.length != AUTH_TAG_LENGTH_BYTES) {
            throw new MessageDecryptionException("Encrypted message authentication tag has an invalid length");
        }

        byte[] sealed = new byte[ciphertextBytes.length + authTagBytes.length];
        System.arraycopy(ciphertextBytes, 0, sealed, 0, ciphertextBytes.length);
        System.arraycopy(authTagBytes, 0, sealed, ciphertextBytes.length, authTagBytes.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonceBytes));
            byte[] plaintext = cipher.doFinal(sealed);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new MessageAuthenticationException("Message authentication failed", ex);
        } catch (GeneralSecurityException ex) {
            throw new MessageDecryptionException("Encrypted message could not be decrypted", ex);
        } finally {
            Arrays.fill(sealed, (byte) 0);
        }
    }

    private void parsePreviousKeys(String previousKeys, Map<String, SecretKey> target) {
        if (!StringUtils.hasText(previousKeys)) {
            return;
        }

        for (String rawEntry : previousKeys.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalStateException(
                        "MESSAGE_ENCRYPTION_PREVIOUS_KEYS must be a comma-separated list of keyId:base64Key entries.");
            }
            String keyId = entry.substring(0, separator).trim();
            String encodedKey = entry.substring(separator + 1).trim();
            validateKeyId(keyId, "previous encryption key ID");
            if (target.containsKey(keyId)) {
                throw new IllegalStateException("Duplicate previous encryption key ID: " + keyId);
            }
            target.put(keyId, parseKey(encodedKey, "previous key [" + keyId + "]"));
        }
    }

    private SecretKey parseKey(String base64Key, String description) {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException(
                    "Message encryption is enabled but no key is configured for " + description
                            + ". Set MESSAGE_ENCRYPTION_KEY to a Base64-encoded 32-byte key.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Message encryption key for " + description + " is not valid Base64.", ex);
        }

        try {
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "Message encryption key for " + description + " must decode to exactly 32 bytes (256 bits).");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private void validateKeyId(String keyId, String description) {
        if (!StringUtils.hasText(keyId) || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new IllegalStateException(
                    "Invalid " + description + ". Use 1-64 characters from [A-Za-z0-9._-].");
        }
    }

    private boolean isLegacy(Integer version) {
        return version == null || version == 0;
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
