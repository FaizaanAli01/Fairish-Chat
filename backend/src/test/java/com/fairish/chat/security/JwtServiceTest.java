package com.fairish.chat.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Should reject a missing JWT secret")
    void shouldRejectMissingSecret() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "   ");

        assertThrows(IllegalStateException.class, jwtService::validateConfiguration);
    }

    @Test
    @DisplayName("Should reject a JWT secret shorter than 32 bytes")
    void shouldRejectShortSecret() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "too-short");

        assertThrows(IllegalStateException.class, jwtService::validateConfiguration);
    }

    @Test
    @DisplayName("Should accept a JWT secret of at least 32 bytes")
    void shouldAcceptStrongSecret() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "0123456789abcdef0123456789abcdef");

        assertDoesNotThrow(jwtService::validateConfiguration);
    }

    @Test
    @DisplayName("Should fail Spring context startup when the JWT secret is missing")
    void shouldFailContextStartupWhenSecretMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtService.class)
                .withPropertyValues("app.jwt.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("JWT_SECRET is not configured");
                });
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
    @DisplayName("Should not embed email in JWT claims but keep the user id")
    void shouldNotEmbedEmailInToken() {
        com.fairish.chat.model.User user = com.fairish.chat.model.User.builder()
                .id("user-id-1")
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();

        String token = jwtService.generateToken(UserPrincipal.create(user));
        Claims claims = jwtService.extractAllClaims(token);

        assertFalse(claims.containsKey("email"));
        assertEquals("user-id-1", claims.get("id"));
        assertEquals("testuser", claims.getSubject());
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

