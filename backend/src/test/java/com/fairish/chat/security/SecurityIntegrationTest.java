package com.fairish.chat.security;

import com.fairish.chat.controller.HealthController;
import com.fairish.chat.controller.UserController;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, UserController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private UserService userService;

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
}

