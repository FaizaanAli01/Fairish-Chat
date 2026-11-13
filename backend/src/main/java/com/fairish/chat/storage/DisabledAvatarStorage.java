package com.fairish.chat.storage;

import com.fairish.chat.exception.AvatarStorageException;

import java.util.Optional;

/**
 * Storage used when avatars are explicitly disabled. Keeps the application
 * bootable for environments that do not run MinIO/R2 while making it clear at
 * request time that the feature is unavailable.
 */
public class DisabledAvatarStorage implements AvatarStorage {

    private static final String MESSAGE = "Avatar storage is disabled";

    @Override
    public void put(String key, byte[] content, String contentType) {
        throw new AvatarStorageException(MESSAGE);
    }

    @Override
    public Optional<StoredAvatar> get(String key) {
        throw new AvatarStorageException(MESSAGE);
    }

    @Override
    public void delete(String key) {
        throw new AvatarStorageException(MESSAGE);
    }
}
