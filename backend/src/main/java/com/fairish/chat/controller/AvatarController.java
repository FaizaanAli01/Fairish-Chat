package com.fairish.chat.controller;

import com.fairish.chat.dto.UserResponse;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.AvatarContent;
import com.fairish.chat.service.AvatarService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Avatar endpoints. Delivery is always proxied through the authenticated
 * backend; clients never receive object URLs or presigned URLs.
 */
@RestController
@RequestMapping("/api/users")
public class AvatarController {

    private final AvatarService avatarService;
    private final UserRepository userRepository;

    public AvatarController(AvatarService avatarService, UserRepository userRepository) {
        this.avatarService = avatarService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(avatarService.uploadAvatar(extractUserId(userDetails), file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(avatarService.deleteAvatar(extractUserId(userDetails)));
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String targetUserId) {
        AvatarContent content = avatarService.getAvatar(extractUserId(userDetails), targetUserId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (RuntimeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
                        .cachePrivate()
                        .mustRevalidate())
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.content());
    }

    private String extractUserId(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return userRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
    }
}
