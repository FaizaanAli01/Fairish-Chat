package com.fairish.chat.controller;

import com.fairish.chat.dto.MessageDeliveryRequest;
import com.fairish.chat.dto.MessageDeliveryResponse;
import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.PaginatedResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    public MessageController(MessageService messageService, UserRepository userRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        String currentUserId = extractUserId(userDetails);
        MessageResponse response = messageService.sendMessage(conversationId, currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Acknowledges device delivery for one or more incoming messages. The
     * authenticated user is always treated as the recipient; identifiers that
     * do not belong to this conversation or were not sent by the other
     * participant are ignored.
     */
    @PostMapping("/delivered")
    public ResponseEntity<MessageDeliveryResponse> markDelivered(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody MessageDeliveryRequest request) {
        String currentUserId = extractUserId(userDetails);
        MessageDeliveryResponse response = messageService.markDelivered(
                conversationId, currentUserId, request.getMessageIds());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<MessageResponse>> getMessageHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("conversationId") String conversationId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        String currentUserId = extractUserId(userDetails);
        PaginatedResponse<MessageResponse> response = messageService.getMessageHistory(conversationId, currentUserId, page, size);
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
