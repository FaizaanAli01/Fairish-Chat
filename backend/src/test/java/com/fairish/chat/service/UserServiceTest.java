package com.fairish.chat.service;

import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.dto.UpdateProfileRequest;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id("12345")
                .username("john_doe")
                .email("john@example.com")
                .passwordHash("hashedPassword")
                .bio("Hello world")
                .profileImage("https://example.com/avatar.png")
                .online(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return user profile by username")
    void shouldGetCurrentUser() {
        when(userRepository.findByUsernameIgnoreCase("john_doe")).thenReturn(Optional.of(mockUser));

        UserResponse response = userService.getCurrentUser("john_doe");

        assertNotNull(response);
        assertEquals("john_doe", response.getUsername());
        assertEquals("john@example.com", response.getEmail());
        assertEquals("Hello world", response.getBio());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user does not exist")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsernameIgnoreCase("nonexistent")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("nonexistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser("nonexistent"));
    }

    @Test
    @DisplayName("Should update user profile successfully")
    void shouldUpdateProfileSuccessfully() {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .username("new_username")
                .bio("Updated bio")
                .profileImage("https://example.com/new.png")
                .build();

        when(userRepository.findByUsernameIgnoreCase("john_doe")).thenReturn(Optional.of(mockUser));
        when(userRepository.existsByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateProfile("john_doe", updateRequest);

        assertNotNull(response);
        assertEquals("new_username", response.getUsername());
        assertEquals("Updated bio", response.getBio());
        assertEquals("https://example.com/new.png", response.getProfileImage());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when updating to an existing username")
    void shouldThrowWhenNewUsernameTaken() {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .username("taken_username")
                .build();

        when(userRepository.findByUsernameIgnoreCase("john_doe")).thenReturn(Optional.of(mockUser));
        when(userRepository.existsByUsernameIgnoreCase("taken_username")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.updateProfile("john_doe", updateRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should search users excluding current user")
    void shouldSearchUsers() {
        User otherUser = User.builder()
                .id("67890")
                .username("jane_doe")
                .email("jane@example.com")
                .build();

        when(userRepository.searchByUsername(anyString())).thenReturn(List.of(mockUser, otherUser));

        List<PublicUserResponse> results = userService.searchUsers("john_doe", "doe");

        assertEquals(1, results.size());
        assertEquals("jane_doe", results.get(0).getUsername());
    }
}

