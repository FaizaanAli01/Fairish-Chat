package com.fairish.chat.storage;

import java.util.Optional;

/**
 * Provider-agnostic avatar object storage. Implementations must use private
 * buckets only; object URLs are never exposed to clients.
 */
public interface AvatarStorage {

    void put(String key, byte[] content, String contentType);

    Optional<StoredAvatar> get(String key);

    void delete(String key);
}
