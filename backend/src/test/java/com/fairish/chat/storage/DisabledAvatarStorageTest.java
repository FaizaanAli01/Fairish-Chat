package com.fairish.chat.storage;

import com.fairish.chat.exception.AvatarStorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DisabledAvatarStorageTest {

    private final DisabledAvatarStorage storage = new DisabledAvatarStorage();

    @Test
    @DisplayName("Disabled storage rejects reads, writes, and deletes with a storage exception")
    void shouldRejectAllOperations() {
        assertThrows(AvatarStorageException.class,
                () -> storage.put("avatars/key.png", new byte[]{1}, "image/png"));
        assertThrows(AvatarStorageException.class, () -> storage.get("avatars/key.png"));
        assertThrows(AvatarStorageException.class, () -> storage.delete("avatars/key.png"));
    }
}
