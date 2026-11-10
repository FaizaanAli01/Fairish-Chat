package com.fairish.chat.service;

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

    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String currentUsername, String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        String safeRegex = Pattern.quote(query.trim());
        List<User> matchedUsers = userRepository.searchByUsername(safeRegex);

        return matchedUsers.stream()
                .filter(u -> !u.getUsername().equalsIgnoreCase(currentUsername))
                .map(UserResponse::fromUser)
                .collect(Collectors.toList());
    }
}
