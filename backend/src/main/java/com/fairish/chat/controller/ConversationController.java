package com.fairish.chat.controller;

import com.fairish.chat.dto.ConversationResponse;
import com.fairish.chat.dto.CreateConversationRequest;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createOrGetConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateConversationRequest request) {
        String currentUserId = extractUserId(userDetails);
        ConversationResponse response = conversationService.getOrCreateConversation(currentUserId, request.getRecipientId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getUserConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        String currentUserId = extractUserId(userDetails);
        List<ConversationResponse> conversations = conversationService.getUserConversations(currentUserId);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversationById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String id) {
        String currentUserId = extractUserId(userDetails);
        ConversationResponse response = conversationService.getConversationById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks the conversation as viewed for the authenticated user by resetting
     * only that user's unread counter.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<ConversationResponse> markConversationRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String id) {
        String currentUserId = extractUserId(userDetails);
        ConversationResponse response = conversationService.markConversationRead(id, currentUserId);
        return ResponseEntity.ok(response);
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
