package com.fairish.chat.controller;

import com.fairish.chat.dto.ConversationResponse;
import com.fairish.chat.dto.CreateConversationRequest;
import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.security.ClientIpResolver;
import com.fairish.chat.security.RateLimiterService;
import com.fairish.chat.security.TokenBlacklistService;
import com.fairish.chat.service.ConversationService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RateLimiterService.class, ClientIpResolver.class})
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private User currentUser;
    private ConversationResponse conversationResponse;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id("current-user-id")
                .username("alice")
                .email("alice@example.com")
                .build();

        User otherUser = User.builder()
                .id("other-user-id")
                .username("bob")
                .email("bob@example.com")
                .build();

        conversationResponse = ConversationResponse.builder()
                .id("conv-123")
                .participantIds(List.of("current-user-id", "other-user-id"))
                .otherUser(PublicUserResponse.fromUser(otherUser))
                .unreadCount(4)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(currentUser));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations should create or retrieve conversation with 201 Created")
    void shouldCreateOrGetConversation() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("other-user-id");
        when(conversationService.getOrCreateConversation(eq("current-user-id"), eq("other-user-id")))
                .thenReturn(conversationResponse);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("conv-123"))
                .andExpect(jsonPath("$.otherUser.username").value("bob"))
                .andExpect(jsonPath("$.otherUser.email").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations should fail validation when recipientId is blank")
    void shouldFailValidationForBlankRecipient() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("");

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/conversations should return user's conversations")
    void shouldListUserConversations() throws Exception {
        when(conversationService.getUserConversations("current-user-id"))
                .thenReturn(List.of(conversationResponse));

        mockMvc.perform(get("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("conv-123"))
                .andExpect(jsonPath("$[0].unreadCount").value(4));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/conversations/{id} should return conversation for participant")
    void shouldGetConversationById() throws Exception {
        when(conversationService.getConversationById("conv-123", "current-user-id"))
                .thenReturn(conversationResponse);

        mockMvc.perform(get("/api/conversations/conv-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv-123"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/conversations/{id} should return 403 Forbidden for non-participant")
    void shouldReturnForbiddenForNonParticipant() throws Exception {
        when(conversationService.getConversationById("conv-123", "current-user-id"))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        mockMvc.perform(get("/api/conversations/conv-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations/{id}/read should reset the authenticated user's unread count")
    void shouldMarkConversationRead() throws Exception {
        ConversationResponse readResponse = ConversationResponse.builder()
                .id("conv-123")
                .participantIds(List.of("current-user-id", "other-user-id"))
                .unreadCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(conversationService.markConversationRead("conv-123", "current-user-id"))
                .thenReturn(readResponse);

        mockMvc.perform(post("/api/conversations/conv-123/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv-123"))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations/{id}/read should return 403 for non-participants")
    void shouldRejectMarkingReadForNonParticipant() throws Exception {
        when(conversationService.markConversationRead("conv-123", "current-user-id"))
                .thenThrow(new AccessDeniedException("You are not a participant in this conversation"));

        mockMvc.perform(post("/api/conversations/conv-123/read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/conversations should return 403 when users are not accepted contacts")
    void shouldReturnForbiddenWhenNotAcceptedContacts() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("other-user-id");
        when(conversationService.getOrCreateConversation(eq("current-user-id"), eq("other-user-id")))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }
}
