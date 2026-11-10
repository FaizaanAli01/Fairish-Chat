package com.fairish.chat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Should generate valid JWT token for UserDetails")
    void shouldGenerateValidToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String token = jwtService.generateToken(userDetails);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals("testuser", jwtService.extractUsername(token));
        assertTrue(jwtService.validateToken(token, userDetails));
    }

    @Test
    @DisplayName("Should fail validation for different username")
    void shouldFailValidationForDifferentUser() {
        UserDetails user1 = new User("user1", "password", Collections.emptyList());
        UserDetails user2 = new User("user2", "password", Collections.emptyList());

        String token = jwtService.generateToken(user1);
        assertFalse(jwtService.validateToken(token, user2));
    }

    @Test
    @DisplayName("Should detect expired token")
    void shouldDetectExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1000L); // Expired 1 second ago
        UserDetails user = new User("expireduser", "password", Collections.emptyList());

        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isTokenExpired(token));
        assertFalse(jwtService.validateToken(token, user));
    }
}

