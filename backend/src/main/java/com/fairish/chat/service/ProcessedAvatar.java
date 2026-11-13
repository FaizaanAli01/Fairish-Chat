package com.fairish.chat.service;

/**
 * Result of validating and normalizing an uploaded avatar.
 */
public record ProcessedAvatar(byte[] content, String contentType, int width, int height, AvatarFormat format) {
}
