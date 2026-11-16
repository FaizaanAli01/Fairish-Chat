package com.fairish.chat.security;

import com.fairish.chat.controller.AvatarController;
import com.fairish.chat.controller.HealthController;
import com.fairish.chat.controller.UserController;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.service.AvatarService;
import com.fairish.chat.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, UserController.class, AvatarController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class,
        RateLimiterService.class, ClientIpResolver.class})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private UserService userService;

    @MockBean
    private AvatarService avatarService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("Public endpoint /api/health should be accessible without token")
    void healthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Protected endpoint /api/users/me should reject unauthenticated requests with 401")
    void protectedEndpointShouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Protected endpoint should succeed when valid Bearer token provided")
    void protectedEndpointShouldSucceedWithValidBearerToken() throws Exception {
        String validToken = "valid.jwt.token";
        UserDetails userDetails = new User("john_doe", "password", Collections.emptyList());
        UserResponse mockResponse = UserResponse.builder().id("1").username("john_doe").email("john@example.com").build();

        when(jwtService.extractUsername(validToken)).thenReturn("john_doe");
        when(userDetailsService.loadUserByUsername("john_doe")).thenReturn(userDetails);
        when(jwtService.validateToken(validToken, userDetails)).thenReturn(true);
        when(userService.getCurrentUser("john_doe")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"));
    }

    @Test
    @DisplayName("Protected endpoint should reject blacklisted tokens with 401")
    void protectedEndpointShouldRejectBlacklistedToken() throws Exception {
        String blacklistedToken = "blacklisted.jwt.token";
        when(tokenBlacklistService.isBlacklisted(blacklistedToken)).thenReturn(true);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + blacklistedToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint should return 503 when the blacklist lookup fails")
    void protectedEndpointShouldFailClosedWhenBlacklistLookupFails() throws Exception {
        String token = "valid.jwt.token";
        when(tokenBlacklistService.isBlacklisted(token))
                .thenThrow(new RuntimeException("Blacklist store unavailable"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("Avatar delivery should reject unauthenticated requests with 401")
    void avatarDeliveryShouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/user-2/avatar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Avatar upload should reject unauthenticated requests with 401")
    void avatarUploadShouldRejectUnauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me/avatar").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}

