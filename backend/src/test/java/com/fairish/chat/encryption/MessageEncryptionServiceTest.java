package com.fairish.chat.encryption;

import com.fairish.chat.exception.MessageDecryptionException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class MessageEncryptionServiceTest {

    private static final String KEY = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVpBQkNERUY=";
    private static final String OTHER_KEY = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWY=";

    @Test
    @DisplayName("Should encrypt and decrypt a message with AES-256-GCM")
    void shouldEncryptAndDecrypt() {
        MessageEncryptionService service = service(KEY, "v1", null);

        EncryptedPayload payload = service.encrypt("Secret message");
        String decrypted = service.decrypt(message(payload));

        assertEquals("Secret message", decrypted);
        assertEquals(1, payload.encryptionVersion());
        assertEquals("v1", payload.keyId());
        assertEquals(12, Base64.getDecoder().decode(payload.nonce()).length);
        assertEquals(16, Base64.getDecoder().decode(payload.authTag()).length);
    }

    @Test
    @DisplayName("Should use a fresh nonce and different ciphertext for identical plaintext")
    void shouldUseUniqueNoncesForIdenticalPlaintext() {
        MessageEncryptionService service = service(KEY, "v1", null);

        EncryptedPayload first = service.encrypt("Same text");
        EncryptedPayload second = service.encrypt("Same text");

        assertNotEquals(first.nonce(), second.nonce());
        assertNotEquals(first.ciphertext(), second.ciphertext());
        assertEquals("Same text", service.decrypt(first));
        assertEquals("Same text", service.decrypt(second));
    }

    @Test
    @DisplayName("Should reject tampered ciphertext")
    void shouldRejectTamperedCiphertext() {
        MessageEncryptionService service = service(KEY, "v1", null);
        EncryptedPayload payload = service.encrypt("Original text");
        byte[] ciphertext = Base64.getDecoder().decode(payload.ciphertext());
        ciphertext[0] ^= 0x01;

        Message tampered = message(new EncryptedPayload(
                Base64.getEncoder().encodeToString(ciphertext),
                payload.nonce(), payload.authTag(), payload.encryptionVersion(), payload.keyId()));

        assertThrows(MessageDecryptionException.class, () -> service.decrypt(tampered));
    }

    @Test
    @DisplayName("Should reject a tampered authentication tag")
    void shouldRejectTamperedAuthTag() {
        MessageEncryptionService service = service(KEY, "v1", null);
        EncryptedPayload payload = service.encrypt("Original text");
        byte[] tag = Base64.getDecoder().decode(payload.authTag());
        tag[0] ^= 0x01;

        Message tampered = message(new EncryptedPayload(payload.ciphertext(), payload.nonce(),
                Base64.getEncoder().encodeToString(tag), payload.encryptionVersion(), payload.keyId()));

        assertThrows(MessageDecryptionException.class, () -> service.decrypt(tampered));
    }

    @Test
    @DisplayName("Should reject decryption with an incorrect key")
    void shouldRejectIncorrectKey() {
        MessageEncryptionService encryptor = service(KEY, "v1", null);
        MessageEncryptionService differentKey = service(OTHER_KEY, "v1", null);
        EncryptedPayload payload = encryptor.encrypt("Only the original key can read this");

        assertThrows(MessageDecryptionException.class,
                () -> differentKey.decrypt(message(payload)));
    }

    @Test
    @DisplayName("Should reject a nonce with an invalid length")
    void shouldRejectInvalidNonceLength() {
        MessageEncryptionService service = service(KEY, "v1", null);
        EncryptedPayload payload = service.encrypt("Nonce check");
        Message invalid = message(new EncryptedPayload(payload.ciphertext(),
                Base64.getEncoder().encodeToString(new byte[8]),
                payload.authTag(), payload.encryptionVersion(), payload.keyId()));

        MessageDecryptionException ex = assertThrows(MessageDecryptionException.class,
                () -> service.decrypt(invalid));
        assertTrue(ex.getMessage().contains("nonce"));
    }

    @Test
    @DisplayName("Should reject a missing or invalid encryption key at startup")
    void shouldFailFastForInvalidKeyConfiguration() {
        assertThrows(IllegalStateException.class, () -> service(null, "v1", null));
        assertThrows(IllegalStateException.class, () -> service("", "v1", null));
        assertThrows(IllegalStateException.class, () -> service("not-base64!", "v1", null));
        assertThrows(IllegalStateException.class, () -> service(
                Base64.getEncoder().encodeToString(new byte[16]), "v1", null));
    }

    @Test
    @DisplayName("Should reject an invalid key identifier")
    void shouldRejectInvalidKeyId() {
        assertThrows(IllegalStateException.class, () -> service(KEY, "bad key id", null));
    }

    @Test
    @DisplayName("Should not silently fall back to plaintext when encryption is disabled")
    void shouldRefuseToEncryptWhenDisabled() {
        MessageEncryptionProperties properties = new MessageEncryptionProperties();
        properties.setEnabled(false);
        MessageEncryptionService service = new MessageEncryptionService(properties);

        assertFalse(service.isEnabled());
        assertThrows(IllegalStateException.class, () -> service.encrypt("No plaintext fallback"));
    }

    @Test
    @DisplayName("Should read legacy plaintext records without an encryption version")
    void shouldReadLegacyPlaintext() {
        MessageEncryptionService service = service(KEY, "v1", null);
        Message legacy = Message.builder()
                .id("legacy")
                .conversationId("conv")
                .senderId("user")
                .content("Historical plaintext")
                .createdAt(Instant.now())
                .build();

        assertEquals("Historical plaintext", service.decrypt(legacy));
    }

    @Test
    @DisplayName("Should reject an unsupported encryption version")
    void shouldRejectUnsupportedVersion() {
        MessageEncryptionService service = service(KEY, "v1", null);
        Message message = Message.builder()
                .id("future")
                .conversationId("conv")
                .senderId("user")
                .ciphertext("abc")
                .nonce("abc")
                .authTag("abc")
                .encryptionVersion(99)
                .keyId("v1")
                .createdAt(Instant.now())
                .build();

        assertThrows(MessageDecryptionException.class, () -> service.decrypt(message));
    }

    @Test
    @DisplayName("Should decrypt records encrypted with a rotated-out previous key")
    void shouldDecryptWithPreviousKeyAfterRotation() {
        MessageEncryptionService oldService = service(KEY, "v1", null);
        EncryptedPayload oldPayload = oldService.encrypt("Before rotation");

        MessageEncryptionService rotated = service(OTHER_KEY, "v2", "v1:" + KEY);
        assertEquals("Before rotation", rotated.decrypt(message(oldPayload)));

        EncryptedPayload newPayload = rotated.encrypt("After rotation");
        assertEquals("v2", newPayload.keyId());
        assertEquals("After rotation", rotated.decrypt(message(newPayload)));
    }

    @Test
    @DisplayName("Should decrypt conversation previews and hide undecryptable ones")
    void shouldDecryptConversationPreview() {
        MessageEncryptionService service = service(KEY, "v1", null);
        EncryptedPayload payload = service.encrypt("Preview text");

        Conversation encrypted = Conversation.builder()
                .id("conv-1")
                .lastMessageCiphertext(payload.ciphertext())
                .lastMessageNonce(payload.nonce())
                .lastMessageAuthTag(payload.authTag())
                .lastMessageEncryptionVersion(payload.encryptionVersion())
                .lastMessageKeyId(payload.keyId())
                .build();
        assertEquals("Preview text", service.decryptConversationPreview(encrypted));

        Conversation tampered = Conversation.builder()
                .id("conv-2")
                .lastMessageCiphertext(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}))
                .lastMessageNonce(payload.nonce())
                .lastMessageAuthTag(payload.authTag())
                .lastMessageEncryptionVersion(1)
                .lastMessageKeyId("v1")
                .build();
        assertEquals(MessageEncryptionService.PREVIEW_UNAVAILABLE,
                service.decryptConversationPreview(tampered));
    }

    private MessageEncryptionService service(String key, String activeKeyId, String previousKeys) {
        MessageEncryptionProperties properties = new MessageEncryptionProperties();
        properties.setEnabled(true);
        properties.setKey(key);
        properties.setActiveKeyId(activeKeyId);
        properties.setPreviousKeys(previousKeys);
        return new MessageEncryptionService(properties);
    }

    private Message message(EncryptedPayload payload) {
        return Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId("user-1")
                .ciphertext(payload.ciphertext())
                .nonce(payload.nonce())
                .authTag(payload.authTag())
                .encryptionVersion(payload.encryptionVersion())
                .keyId(payload.keyId())
                .createdAt(Instant.now())
                .build();
    }
}
