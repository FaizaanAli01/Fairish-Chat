package com.fairish.chat.controller;

import com.fairish.chat.dto.MessageResponse;
import com.fairish.chat.dto.PaginatedResponse;
import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private User currentUser;
    private MessageResponse messageResponse;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id("current-user-id")
                .username("alice")
                .email("alice@example.com")
                .build();

        messageResponse = MessageResponse.builder()
                .id("msg-1")
                .conversationId("conv-123")
                .senderId("current-user-id")
                .content("Hello Bob!")
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(currentUser));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations/{convId}/messages should persist message with 201 Created")
    void shouldSendMessage() throws Exception {
        SendMessageRequest request = new SendMessageRequest("Hello Bob!");
        when(messageService.sendMessage(eq("conv-123"), eq("current-user-id"), any(SendMessageRequest.class)))
                .thenReturn(messageResponse);

        mockMvc.perform(post("/api/conversations/conv-123/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("msg-1"))
                .andExpect(jsonPath("$.content").value("Hello Bob!"))
                .andExpect(jsonPath("$.senderId").value("current-user-id"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations/{convId}/messages should fail with 400 for blank content")
    void shouldFailForBlankMessage() throws Exception {
        SendMessageRequest request = new SendMessageRequest("");

        mockMvc.perform(post("/api/conversations/conv-123/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/conversations/{convId}/messages should return paginated message history")
    void shouldGetMessageHistory() throws Exception {
        PaginatedResponse<MessageResponse> paginated = new PaginatedResponse<>(
                List.of(messageResponse), 0, 20, 1, 1, false
        );

        when(messageService.getMessageHistory(eq("conv-123"), eq("current-user-id"), eq(0), eq(20)))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/conversations/conv-123/messages?page=0&size=20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("Hello Bob!"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/conversations/{convId}/messages should return 403 Forbidden for non-participant")
    void shouldReturnForbiddenForNonParticipant() throws Exception {
        when(messageService.getMessageHistory(eq("conv-123"), eq("current-user-id"), anyInt(), anyInt()))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        mockMvc.perform(get("/api/conversations/conv-123/messages")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations/{convId}/messages should return 403 when contacts were removed")
    void shouldReturnForbiddenAfterContactRemoval() throws Exception {
        SendMessageRequest request = new SendMessageRequest("Hello again");
        when(messageService.sendMessage(eq("conv-123"), eq("current-user-id"), any(SendMessageRequest.class)))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        mockMvc.perform(post("/api/conversations/conv-123/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }
}
