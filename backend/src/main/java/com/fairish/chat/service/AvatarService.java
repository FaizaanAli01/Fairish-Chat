package com.fairish.chat.service;

import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.AvatarStorageException;
import com.fairish.chat.exception.InvalidAvatarException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.storage.AvatarStorage;
import com.fairish.chat.storage.StoredAvatar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Avatar upload, removal, and contact-gated delivery.
 *
 * <p>Authorization is enforced here and in the streaming controller: only the
 * owner and accepted contacts may read an avatar. Conversation participation
 * alone is never sufficient.</p>
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final String KEY_PREFIX = "avatars/";

    private final UserRepository userRepository;
    private final AvatarStorage avatarStorage;
    private final AvatarImageProcessor imageProcessor;
    private final ContactService contactService;

    public AvatarService(UserRepository userRepository,
                         AvatarStorage avatarStorage,
                         AvatarImageProcessor imageProcessor,
                         ContactService contactService) {
        this.userRepository = userRepository;
        this.avatarStorage = avatarStorage;
        this.imageProcessor = imageProcessor;
        this.contactService = contactService;
    }

    @Transactional
    public UserResponse uploadAvatar(String currentUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException("Please select an image file");
        }
        if (file.getSize() > imageProcessor.getMaxUploadBytes()) {
            throw new InvalidAvatarException("Avatar must be "
                    + (imageProcessor.getMaxUploadBytes() / (1024 * 1024)) + " MB or smaller");
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidAvatarException("The uploaded image could not be read", ex);
        }

        ProcessedAvatar processed = imageProcessor.process(raw);
        String newKey = generateKey(processed.format());

        User user = requireUser(currentUserId);
        String previousKey = user.getAvatarKey();

        // Storage write happens before any profile mutation so a failed upload
        // never leaves the user pointing at a missing object.
        avatarStorage.put(newKey, processed.content(), processed.contentType());

        Instant now = Instant.now();
        user.setAvatarKey(newKey);
        user.setAvatarContentType(processed.contentType());
        user.setAvatarUpdatedAt(now);
        // Uploading an avatar replaces the legacy free-text image URL.
        user.setProfileImage(null);
        user.setUpdatedAt(now);

        try {
            User saved = userRepository.save(user);
            deleteQuietly(previousKey);
            log.info("Avatar updated for user [{}]", currentUserId);
            return UserResponse.fromUser(saved);
        } catch (RuntimeException ex) {
            // Roll back the orphaned object if the profile update failed.
            deleteQuietly(newKey);
            throw ex;
        }
    }

    @Transactional
    public UserResponse deleteAvatar(String currentUserId) {
        User user = requireUser(currentUserId);
        String previousKey = user.getAvatarKey();

        if (previousKey == null) {
            return UserResponse.fromUser(user);
        }

        Instant now = Instant.now();
        user.setAvatarKey(null);
        user.setAvatarContentType(null);
        user.setAvatarUpdatedAt(null);
        user.setUpdatedAt(now);

        // Clear the pointer first; a storage failure must not leave a dangling reference.
        User saved = userRepository.save(user);
        deleteQuietly(previousKey);
        log.info("Avatar removed for user [{}]", currentUserId);
        return UserResponse.fromUser(saved);
    }

    @Transactional(readOnly = true)
    public AvatarContent getAvatar(String currentUserId, String targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean owner = currentUserId != null && currentUserId.equals(targetUserId);
        if (!owner && !contactService.areAcceptedContacts(currentUserId, targetUserId)) {
            log.warn("Avatar access denied for user [{}] on user [{}]", currentUserId, targetUserId);
            throw new AccessDeniedException("You are not allowed to view this user's avatar");
        }

        String key = target.getAvatarKey();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("Avatar not found");
        }

        StoredAvatar stored = avatarStorage.get(key)
                .orElseThrow(() -> new ResourceNotFoundException("Avatar not found"));

        String contentType = target.getAvatarContentType() != null
                ? target.getAvatarContentType()
                : stored.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return new AvatarContent(stored.content(), contentType, target.getAvatarUpdatedAt());
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String generateKey(AvatarFormat format) {
        return KEY_PREFIX + UUID.randomUUID() + "." + format.getExtension();
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            avatarStorage.delete(key);
        } catch (AvatarStorageException ex) {
            // Cleanup is best-effort: never fail the request or expose storage details.
            log.warn("Failed to delete an avatar object from storage");
        }
    }
}
