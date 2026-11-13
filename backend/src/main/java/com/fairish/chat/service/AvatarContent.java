package com.fairish.chat.service;

import java.time.Instant;

/**
 * Authorized avatar bytes returned to the proxy controller.
 */
public record AvatarContent(byte[] content, String contentType, Instant updatedAt) {
}
