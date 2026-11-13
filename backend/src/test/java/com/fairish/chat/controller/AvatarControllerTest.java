package com.fairish.chat.controller;

import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.AvatarStorageException;
import com.fairish.chat.exception.InvalidAvatarException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.service.AvatarContent;
import com.fairish.chat.service.AvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvatarController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AvatarService avatarService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @BeforeEach
    void setUp() {
        User alice = User.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .build();
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
    }

    // ---------------------------------------------------------------------
    // Upload
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/users/me/avatar returns the updated self profile")
    void shouldUploadAvatar() throws Exception {
        UserResponse response = UserResponse.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .hasAvatar(true)
                .avatarUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(avatarService.uploadAvatar(eq("user-1"), any(MultipartFile.class))).thenReturn(response);

        mockMvc.perform(multipart("/api/users/me/avatar").file(pngFile()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.hasAvatar").value(true))
                .andExpect(jsonPath("$.avatarUpdatedAt").exists())
                .andExpect(jsonPath("$.avatarKey").doesNotExist())
                .andExpect(jsonPath("$.avatarContentType").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/users/me/avatar returns 400 when the file part is missing")
    void shouldRejectMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Avatar file is required"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/users/me/avatar returns 400 for an invalid image")
    void shouldRejectInvalidAvatar() throws Exception {
        when(avatarService.uploadAvatar(eq("user-1"), any(MultipartFile.class)))
                .thenThrow(new InvalidAvatarException("WebP images are not supported. Upload a JPEG or PNG."));

        mockMvc.perform(multipart("/api/users/me/avatar").file(pngFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("WebP images are not supported. Upload a JPEG or PNG."));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/users/me/avatar returns 413 when the transport limit is exceeded")
    void shouldReturnPayloadTooLarge() throws Exception {
        when(avatarService.uploadAvatar(eq("user-1"), any(MultipartFile.class)))
                .thenThrow(new MaxUploadSizeExceededException(5 * 1024 * 1024));

        mockMvc.perform(multipart("/api/users/me/avatar").file(pngFile()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Avatar must be 5 MB or smaller"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/users/me/avatar returns a generic 503 on storage failure")
    void shouldReturnServiceUnavailableOnStorageFailure() throws Exception {
        when(avatarService.uploadAvatar(eq("user-1"), any(MultipartFile.class)))
                .thenThrow(new AvatarStorageException("S3 endpoint refused connection to internal-host:9000"));

        mockMvc.perform(multipart("/api/users/me/avatar").file(pngFile()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Avatar storage is temporarily unavailable. Please try again later."));
    }

    // ---------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("DELETE /api/users/me/avatar clears the avatar")
    void shouldDeleteAvatar() throws Exception {
        UserResponse response = UserResponse.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .hasAvatar(false)
                .build();
        when(avatarService.deleteAvatar("user-1")).thenReturn(response);

        mockMvc.perform(delete("/api/users/me/avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(false));
    }

    // ---------------------------------------------------------------------
    // Authorized delivery
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/users/{id}/avatar streams bytes with private, revalidating cache headers")
    void shouldStreamAvatarWithSafeHeaders() throws Exception {
        byte[] bytes = {1, 2, 3, 4, 5};
        when(avatarService.getAvatar("user-1", "user-2"))
                .thenReturn(new AvatarContent(bytes, "image/png", Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(get("/api/users/user-2/avatar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("private"),
                        containsString("max-age=300"),
                        containsString("must-revalidate"))))
                .andExpect(header().string("Vary", containsString("Authorization")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/users/{id}/avatar falls back to octet-stream for an invalid stored content type")
    void shouldFallbackContentType() throws Exception {
        when(avatarService.getAvatar("user-1", "user-2"))
                .thenReturn(new AvatarContent(new byte[]{1}, "not-a-media-type", null));

        mockMvc.perform(get("/api/users/user-2/avatar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/users/{id}/avatar returns 403 when the viewer is not an accepted contact")
    void shouldReturnForbiddenForUnauthorizedViewer() throws Exception {
        when(avatarService.getAvatar("user-1", "user-2"))
                .thenThrow(new AccessDeniedException("You are not allowed to view this user's avatar"));

        mockMvc.perform(get("/api/users/user-2/avatar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/users/{id}/avatar returns 404 for an unknown user or missing avatar")
    void shouldReturnNotFound() throws Exception {
        when(avatarService.getAvatar("user-1", "ghost"))
                .thenThrow(new ResourceNotFoundException("Avatar not found"));

        mockMvc.perform(get("/api/users/ghost/avatar"))
                .andExpect(status().isNotFound());
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});
    }
}
