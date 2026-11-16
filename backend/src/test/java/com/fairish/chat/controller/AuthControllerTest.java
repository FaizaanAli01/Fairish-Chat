package com.fairish.chat.controller;

import com.fairish.chat.dto.AuthResponse;
import com.fairish.chat.dto.LoginRequest;
import com.fairish.chat.dto.RegisterRequest;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.InvalidCredentialsException;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtAuthenticationFilter;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.security.ClientIpResolver;
import com.fairish.chat.security.RateLimiterService;
import com.fairish.chat.security.TokenBlacklistService;
import com.fairish.chat.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RateLimiterService.class, ClientIpResolver.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .build();

        validLoginRequest = LoginRequest.builder()
                .identifier("test@example.com")
                .password("password123")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id("123")
                .username("testuser")
                .email("test@example.com")
                .online(false)
                .createdAt(Instant.now())
                .build();

        mockAuthResponse = AuthResponse.builder()
                .token("valid.jwt.token")
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(userResponse)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register - Success should return 201 Created with AuthResponse")
    void shouldRegisterSuccessfully() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("valid.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Invalid email should return 400 Bad Request")
    void shouldFailRegistrationOnInvalidEmail() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .username("validUser")
                .email("invalid-email")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - Short password should return 400 Bad Request")
    void shouldFailRegistrationOnShortPassword() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .username("validUser")
                .email("valid@example.com")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate email should return 409 Conflict")
    void shouldReturnConflictOnDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Success should return 200 OK with AuthResponse")
    void shouldLoginSuccessfully() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("valid.jwt.token"))
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid credentials should return 401 Unauthorized")
    void shouldFailLoginOnInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid username/email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username/email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - Valid token should be blacklisted until expiration")
    void shouldLogoutSuccessfully() throws Exception {
        String token = "valid.jwt.token";
        Date expiration = Date.from(Instant.now().plusSeconds(3600));
        when(jwtService.extractExpiration(token)).thenReturn(expiration);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        verify(tokenBlacklistService).blacklistToken(token, expiration.toInstant());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Missing Authorization header should return 401 Unauthorized")
    void shouldRejectLogoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Authorization header with a Bearer token is required."));

        verifyNoInteractions(jwtService, tokenBlacklistService);
    }

    @Test
    @DisplayName("POST /api/auth/logout - Malformed Authorization header should return 401 Unauthorized")
    void shouldRejectLogoutWithMalformedHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Token valid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Authorization header must use the Bearer scheme."));

        verifyNoInteractions(jwtService, tokenBlacklistService);
    }

    @Test
    @DisplayName("POST /api/auth/logout - Invalid JWT signature should return 401 Unauthorized")
    void shouldRejectLogoutWithInvalidJwt() throws Exception {
        String token = "invalid.jwt.token";
        when(jwtService.extractExpiration(token))
                .thenThrow(new SignatureException("JWT signature does not match"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The authentication token is invalid."));

        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    @DisplayName("POST /api/auth/logout - Expired JWT should return 401 Unauthorized")
    void shouldRejectLogoutWithExpiredJwt() throws Exception {
        String token = "expired.jwt.token";
        when(jwtService.extractExpiration(token))
                .thenThrow(new ExpiredJwtException(null, null, "JWT expired"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The authentication token has expired."));

        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    @DisplayName("POST /api/auth/logout - Blacklist failure should return 503 Service Unavailable")
    void shouldFailLogoutWhenBlacklistingFails() throws Exception {
        String token = "valid.jwt.token";
        Date expiration = Date.from(Instant.now().plusSeconds(3600));
        when(jwtService.extractExpiration(token)).thenReturn(expiration);
        doThrow(new RuntimeException("Blacklist store unavailable"))
                .when(tokenBlacklistService).blacklistToken(token, expiration.toInstant());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Unable to revoke the authentication token. Please try again later."));

        verify(tokenBlacklistService).blacklistToken(token, expiration.toInstant());
    }
}
