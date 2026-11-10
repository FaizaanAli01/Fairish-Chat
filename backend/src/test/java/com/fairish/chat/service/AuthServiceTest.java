package com.fairish.chat.service;

import com.fairish.chat.dto.AuthResponse;
import com.fairish.chat.dto.LoginRequest;
import com.fairish.chat.dto.RegisterRequest;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.InvalidCredentialsException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("john_doe")
                .email("john@example.com")
                .password("password123")
                .build();

        loginRequest = LoginRequest.builder()
                .identifier("john@example.com")
                .password("password123")
                .build();

        mockUser = User.builder()
                .id("12345")
                .username("john_doe")
                .email("john@example.com")
                .passwordHash("hashedPassword")
                .online(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully register user")
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("john_doe")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(jwtService.generateToken(any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("john_doe", response.getUser().getUsername());
        assertEquals("john@example.com", response.getUser().getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email already exists")
    void shouldThrowWhenEmailExists() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when username already exists")
    void shouldThrowWhenUsernameExists() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("john_doe")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("john_doe", response.getUser().getUsername());
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when user not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("john@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password does not match")
    void shouldThrowWhenPasswordMismatch() {
        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        LoginRequest wrongLogin = LoginRequest.builder()
                .identifier("john@example.com")
                .password("wrongPassword")
                .build();

        assertThrows(InvalidCredentialsException.class, () -> authService.login(wrongLogin));
    }
}

