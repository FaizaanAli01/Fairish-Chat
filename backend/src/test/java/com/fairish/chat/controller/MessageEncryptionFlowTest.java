package com.fairish.chat.controller;

import com.fairish.chat.dto.SendMessageRequest;
import com.fairish.chat.encryption.EncryptedPayload;
import com.fairish.chat.encryption.MessageEncryptionProperties;
import com.fairish.chat.encryption.MessageEncryptionService;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.MessageRepository;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.service.ConversationService;
import com.fairish.chat.service.ConversationUnreadService;
import com.fairish.chat.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real encryption service through the REST layer: the response
 * must contain plaintext while the persisted entity contains only ciphertext.
 */
@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(MessageEncryptionProperties.class)
@Import({MessageService.class, MessageEncryptionService.class})
@TestPropertySource(properties = {
        "app.encryption.enabled=true",
        "app.encryption.active-key-id=v1",
        "app.encryption.key=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVpBQkNERUY="
})
class MessageEncryptionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageEncryptionService encryptionService;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private ConversationUnreadService conversationUnreadService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        User currentUser = User.builder()
                .id("current-user-id")
                .username("alice")
                .email("alice@example.com")
                .build();
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(currentUser));

        conversation = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("current-user-id", "other-user-id"))
                .participantKey("current-user-id:other-user-id")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST should return decrypted plaintext while storing only ciphertext")
    void shouldPersistCiphertextAndReturnPlaintextOverRest() throws Exception {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "current-user-id"))
                .thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId("msg-1");
            return message;
        });
        when(conversationUnreadService.recordMessageAndIncrementUnread(
                eq("conv-123"), eq("current-user-id"), eq("other-user-id"),
                any(EncryptedPayload.class), any(Instant.class)))
                .thenReturn(conversation);

        mockMvc.perform(post("/api/conversations/conv-123/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest("REST plaintext check"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("REST plaintext check"));

        ArgumentCaptor<Message> savedCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(savedCaptor.capture());
        Message saved = savedCaptor.getValue();
        assertNull(saved.getContent());
        assertNotEquals("REST plaintext check", saved.getCiphertext());
        assertEquals("REST plaintext check", encryptionService.decrypt(saved));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET history should decrypt stored ciphertext before returning JSON")
    void shouldReturnDecryptedHistoryOverRest() throws Exception {
        when(conversationService.getConversationAndValidateParticipant("conv-123", "current-user-id"))
                .thenReturn(conversation);

        EncryptedPayload payload = encryptionService.encrypt("Decrypted history item");
        Message stored = Message.builder()
                .id("msg-2")
                .conversationId("conv-123")
                .senderId("other-user-id")
                .ciphertext(payload.ciphertext())
                .nonce(payload.nonce())
                .authTag(payload.authTag())
                .encryptionVersion(payload.encryptionVersion())
                .keyId(payload.keyId())
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored)));

        mockMvc.perform(get("/api/conversations/conv-123/messages?page=0&size=20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Decrypted history item"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
