package com.fairish.chat.controller;

import com.fairish.chat.dto.ChatMessagePayload;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.MessageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketChatController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChatController.class);

    private final MessageService messageService;
    private final UserRepository userRepository;

    public WebSocketChatController(MessageService messageService, UserRepository userRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    /**
     * Handles STOMP SEND to /app/conversations/{conversationId}/sendMessage
     * Payload: { "content": "Hello" }
     */
    @MessageMapping("/conversations/{conversationId}/sendMessage")
    public void sendMessage(@DestinationVariable("conversationId") String conversationId,
                            @Valid @Payload SendMessageRequest request,
                            Principal principal) {
        String currentUserId = extractUserId(principal);
        log.info("Received STOMP message for conversation [{}] from user [{}]", conversationId, currentUserId);
        messageService.sendMessage(conversationId, currentUserId, request);
    }

    /**
     * Handles STOMP SEND to /app/chat.sendMessage
     * Payload: { "conversationId": "...", "content": "Hello" }
     */
    @MessageMapping("/chat.sendMessage")
    public void sendChatMessage(@Valid @Payload ChatMessagePayload payload,
                                Principal principal) {
        String currentUserId = extractUserId(principal);
        log.info("Received STOMP chat message for conversation [{}] from user [{}]", payload.getConversationId(), currentUserId);
        SendMessageRequest request = new SendMessageRequest(payload.getContent());
        messageService.sendMessage(payload.getConversationId(), currentUserId, request);
    }

    private String extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal != null) {
            return userRepository.findByUsernameIgnoreCase(principal.getName())
                    .map(User::getId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getName()));
        }
        throw new AccessDeniedException("User must be authenticated to send messages via WebSocket");
    }
}

