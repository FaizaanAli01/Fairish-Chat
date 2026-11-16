package com.fairish.chat.controller;

import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.dto.UpdateProfileRequest;
import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtAuthenticationFilter;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.security.ClientIpResolver;
import com.fairish.chat.security.RateLimiterService;
import com.fairish.chat.security.TokenBlacklistService;
import com.fairish.chat.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RateLimiterService.class, ClientIpResolver.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserResponse mockUserResponse;

    @BeforeEach
    void setUp() {
        mockUserResponse = UserResponse.builder()
                .id("123")
                .username("john_doe")
                .email("john@example.com")
                .bio("Hello world")
                .profileImage("https://example.com/avatar.png")
                .online(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("GET /api/users/me - Should return current user profile")
    void shouldGetCurrentUserProfile() throws Exception {
        when(userService.getCurrentUser("john_doe")).thenReturn(mockUserResponse);

        mockMvc.perform(get("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.bio").value("Hello world"));
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("PUT /api/users/me - Should update profile and return updated UserResponse")
    void shouldUpdateProfile() throws Exception {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .username("john_updated")
                .bio("New bio")
                .profileImage("https://example.com/new.png")
                .build();

        UserResponse updatedResponse = UserResponse.builder()
                .id("123")
                .username("john_updated")
                .email("john@example.com")
                .bio("New bio")
                .profileImage("https://example.com/new.png")
                .build();

        when(userService.updateProfile(eq("john_doe"), any(UpdateProfileRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_updated"))
                .andExpect(jsonPath("$.bio").value("New bio"));
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("PUT /api/users/me - Should return 409 Conflict when new username is taken")
    void shouldReturnConflictOnTakenUsername() throws Exception {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .username("taken_username")
                .build();

        when(userService.updateProfile(eq("john_doe"), any(UpdateProfileRequest.class)))
                .thenThrow(new DuplicateResourceException("Username is already taken"));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("GET /api/users/search - Should return list of matching users without email")
    void shouldSearchUsers() throws Exception {
        PublicUserResponse user2 = PublicUserResponse.builder()
                .id("456")
                .username("jane_doe")
                .build();

        when(userService.searchUsers("john_doe", "doe")).thenReturn(List.of(user2));

        mockMvc.perform(get("/api/users/search")
                        .param("query", "doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("jane_doe"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }
}

