package com.fairish.chat.storage;

/**
 * Raw avatar bytes plus the content type recorded at upload time.
 */
public record StoredAvatar(byte[] content, String contentType) {
}
