package com.fairish.chat.controller;

import com.fairish.chat.dto.ChatMessagePayload;
import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketChatControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WebSocketChatController webSocketChatController;

    private Principal userPrincipalAuth;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id("user-123")
                .username("alice")
                .email("alice@example.com")
                .build();
        UserPrincipal principal = UserPrincipal.create(user);
        userPrincipalAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Should handle STOMP /conversations/{id}/sendMessage and delegate to MessageService")
    void shouldHandleConversationSendMessage() {
        SendMessageRequest request = new SendMessageRequest("Hello from WebSocket!");
        MessageResponse response = MessageResponse.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId("user-123")
                .content("Hello from WebSocket!")
                .createdAt(Instant.now())
                .build();

        when(messageService.sendMessage(eq("conv-1"), eq("user-123"), any(SendMessageRequest.class)))
                .thenReturn(response);

        webSocketChatController.sendMessage("conv-1", request, userPrincipalAuth);

        verify(messageService).sendMessage(eq("conv-1"), eq("user-123"), eq(request));
    }

    @Test
    @DisplayName("Should handle STOMP /chat.sendMessage and delegate to MessageService")
    void shouldHandleChatSendMessage() {
        ChatMessagePayload payload = new ChatMessagePayload("conv-1", "Broadcast hello");
        MessageResponse response = MessageResponse.builder()
                .id("msg-2")
                .conversationId("conv-1")
                .senderId("user-123")
                .content("Broadcast hello")
                .createdAt(Instant.now())
                .build();

        when(messageService.sendMessage(eq("conv-1"), eq("user-123"), any(SendMessageRequest.class)))
                .thenReturn(response);

        webSocketChatController.sendChatMessage(payload, userPrincipalAuth);

        verify(messageService).sendMessage(eq("conv-1"), eq("user-123"), any(SendMessageRequest.class));
    }
}

