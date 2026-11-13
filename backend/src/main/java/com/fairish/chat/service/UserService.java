package com.fairish.chat.service;

import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.dto.UpdateProfileRequest;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByEmailIgnoreCase(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return UserResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        return UserResponse.fromUser(user);
    }

    @Transactional
    public UserResponse updateProfile(String currentUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(currentUsername)
                .or(() -> userRepository.findByEmailIgnoreCase(currentUsername))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        if (StringUtils.hasText(request.getUsername())) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equalsIgnoreCase(user.getUsername())) {
                if (userRepository.existsByUsernameIgnoreCase(newUsername)) {
                    throw new DuplicateResourceException("Username is already taken");
                }
                user.setUsername(newUsername);
            }
        }

        if (request.getProfileImage() != null) {
            user.setProfileImage(request.getProfileImage().trim());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }

        user.setUpdatedAt(Instant.now());
        User updatedUser = userRepository.save(user);

        log.info("Profile updated for user: {}", updatedUser.getUsername());
        return UserResponse.fromUser(updatedUser);
    }

    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{24}$");

    @Transactional(readOnly = true)
    public List<PublicUserResponse> searchUsers(String currentUsername, String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        String trimmedQuery = query.trim();

        // A 24-hex value is a Fairish User ID; resolve it exactly so users can be
        // found by the identifier they share. Fall back to username search when
        // no user owns that ID.
        if (OBJECT_ID_PATTERN.matcher(trimmedQuery).matches()) {
            Optional<User> byId = userRepository.findById(trimmedQuery);
            if (byId.isPresent()) {
                User user = byId.get();
                if (user.getUsername().equalsIgnoreCase(currentUsername)) {
                    return Collections.emptyList();
                }
                return List.of(PublicUserResponse.fromUser(user));
            }
        }

        String safeRegex = Pattern.quote(trimmedQuery);
        List<User> matchedUsers = userRepository.searchByUsername(safeRegex);

        return matchedUsers.stream()
                .filter(u -> !u.getUsername().equalsIgnoreCase(currentUsername))
                .map(PublicUserResponse::fromUser)
                .collect(Collectors.toList());
    }
}
