package com.fairish.chat.controller;

import com.fairish.chat.dto.ContactRequestResponse;
import com.fairish.chat.dto.ContactResponse;
import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.dto.SendContactRequestRequest;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.InvalidRequestStateException;
import com.fairish.chat.model.ContactStatus;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.CustomUserDetailsService;
import com.fairish.chat.security.JwtAuthenticationEntryPoint;
import com.fairish.chat.security.JwtService;
import com.fairish.chat.security.ClientIpResolver;
import com.fairish.chat.security.RateLimiterService;
import com.fairish.chat.security.TokenBlacklistService;
import com.fairish.chat.service.ContactService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RateLimiterService.class, ClientIpResolver.class})
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContactService contactService;

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

    private ContactRequestResponse pendingResponse;
    private ContactRequestResponse acceptedResponse;
    private ContactResponse contactResponse;

    @BeforeEach
    void setUp() {
        User currentUser = User.builder()
                .id("current-user-id")
                .username("alice")
                .email("alice@example.com")
                .build();

        PublicUserResponse alice = PublicUserResponse.builder()
                .id("current-user-id")
                .username("alice")
                .build();

        PublicUserResponse bob = PublicUserResponse.builder()
                .id("other-user-id")
                .username("bob")
                .build();

        pendingResponse = ContactRequestResponse.builder()
                .id("req-1")
                .requester(alice)
                .recipient(bob)
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        acceptedResponse = ContactRequestResponse.builder()
                .id("req-1")
                .requester(alice)
                .recipient(bob)
                .status(ContactStatus.ACCEPTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .respondedAt(Instant.now())
                .build();

        contactResponse = ContactResponse.builder()
                .user(bob)
                .acceptedAt(Instant.now())
                .build();

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(currentUser));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests should create a pending request with 201 Created")
    void shouldSendContactRequest() throws Exception {
        when(contactService.sendRequest("current-user-id", "other-user-id")).thenReturn(pendingResponse);

        mockMvc.perform(post("/api/contacts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendContactRequestRequest("other-user-id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requester.username").value("alice"))
                .andExpect(jsonPath("$.recipient.username").value("bob"))
                .andExpect(jsonPath("$.requester.email").doesNotExist())
                .andExpect(jsonPath("$.recipient.email").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests should fail validation when recipientId is blank")
    void shouldRejectBlankRecipient() throws Exception {
        mockMvc.perform(post("/api/contacts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendContactRequestRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests should return 409 for a duplicate pending request")
    void shouldReturnConflictForDuplicateRequest() throws Exception {
        when(contactService.sendRequest("current-user-id", "other-user-id"))
                .thenThrow(new DuplicateResourceException("A contact request to this user is already pending"));

        mockMvc.perform(post("/api/contacts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendContactRequestRequest("other-user-id"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/contacts/requests/incoming should list pending incoming requests")
    void shouldListIncomingRequests() throws Exception {
        when(contactService.getIncomingRequests("current-user-id")).thenReturn(List.of(pendingResponse));

        mockMvc.perform(get("/api/contacts/requests/incoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requester.username").value("alice"))
                .andExpect(jsonPath("$[0].requester.email").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/contacts/requests/outgoing should list pending outgoing requests")
    void shouldListOutgoingRequests() throws Exception {
        when(contactService.getOutgoingRequests("current-user-id")).thenReturn(List.of(pendingResponse));

        mockMvc.perform(get("/api/contacts/requests/outgoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipient.username").value("bob"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests/{id}/accept should accept a pending request")
    void shouldAcceptRequest() throws Exception {
        when(contactService.acceptRequest("current-user-id", "req-1")).thenReturn(acceptedResponse);

        mockMvc.perform(post("/api/contacts/requests/req-1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").exists());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests/{id}/accept should return 403 for a non-recipient")
    void shouldReturnForbiddenWhenAcceptingAsNonRecipient() throws Exception {
        when(contactService.acceptRequest("current-user-id", "req-1"))
                .thenThrow(new AccessDeniedException("Only the recipient can accept this contact request"));

        mockMvc.perform(post("/api/contacts/requests/req-1/accept"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests/{id}/accept should return 409 when no longer pending")
    void shouldReturnConflictWhenRequestNotPending() throws Exception {
        when(contactService.acceptRequest("current-user-id", "req-1"))
                .thenThrow(new InvalidRequestStateException("Only pending contact requests can be accepted"));

        mockMvc.perform(post("/api/contacts/requests/req-1/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending contact requests can be accepted"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("POST /api/contacts/requests/{id}/decline should decline a pending request")
    void shouldDeclineRequest() throws Exception {
        ContactRequestResponse declined = ContactRequestResponse.builder()
                .id("req-1")
                .requester(pendingResponse.getRequester())
                .recipient(pendingResponse.getRecipient())
                .status(ContactStatus.DECLINED)
                .respondedAt(Instant.now())
                .build();
        when(contactService.declineRequest("current-user-id", "req-1")).thenReturn(declined);

        mockMvc.perform(post("/api/contacts/requests/req-1/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("DELETE /api/contacts/requests/{id} should cancel a pending request")
    void shouldCancelRequest() throws Exception {
        mockMvc.perform(delete("/api/contacts/requests/req-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /api/contacts should list accepted contacts without email")
    void shouldListContacts() throws Exception {
        when(contactService.getContacts("current-user-id")).thenReturn(List.of(contactResponse));

        mockMvc.perform(get("/api/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.username").value("bob"))
                .andExpect(jsonPath("$[0].user.email").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("DELETE /api/contacts/{userId} should remove an accepted contact")
    void shouldRemoveContact() throws Exception {
        mockMvc.perform(delete("/api/contacts/other-user-id"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("DELETE /api/contacts/{userId} should return 404 for a non-contact")
    void shouldReturnNotFoundWhenRemovingNonContact() throws Exception {
        org.mockito.Mockito.doThrow(new com.fairish.chat.exception.ResourceNotFoundException("Contact not found"))
                .when(contactService).removeContact("current-user-id", "other-user-id");

        mockMvc.perform(delete("/api/contacts/other-user-id"))
                .andExpect(status().isNotFound());
    }
}
