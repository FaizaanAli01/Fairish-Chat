package com.fairish.chat.security;

import com.fairish.chat.model.Conversation;
import com.fairish.chat.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private UserPrincipal testUserPrincipal;

    @BeforeEach
    void setUp() {
        testUserPrincipal = new UserPrincipal(
                "user-123",
                "alice",
                "alice@example.com",
                "encodedPassword",
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("CONNECT frame with valid Bearer token should authenticate user successfully")
    void shouldAuthenticateValidBearerTokenOnConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer valid-jwt-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("valid-jwt-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken("valid-jwt-token", testUserPrincipal)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("alice", resultAccessor.getUser().getName());
        assertTrue(resultAccessor.getUser() instanceof Authentication);
    }

    @Test
    @DisplayName("CONNECT frame with valid 'token' header should authenticate user successfully")
    void shouldAuthenticateValidTokenHeaderOnConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("token", "raw-jwt-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("raw-jwt-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken("raw-jwt-token", testUserPrincipal)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("alice", resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT frame without token should throw AuthenticationCredentialsNotFoundException")
    void shouldRejectMissingTokenOnConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT frame with invalid token should throw AuthenticationCredentialsNotFoundException")
    void shouldRejectInvalidTokenOnConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer invalid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("invalid-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken("invalid-token", testUserPrincipal)).thenReturn(false);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SUBSCRIBE frame to conversation topic by participant should be authorized")
    void shouldAuthorizeParticipantSubscription() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/conv-123");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation sampleConv = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-123", "user-456"))
                .participantKey("user-123:user-456")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenReturn(sampleConv);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(conversationService).getConversationAndValidateParticipant("conv-123", "user-123");
    }

    @Test
    @DisplayName("SUBSCRIBE frame to conversation topic by non-participant should throw AccessDeniedException")
    void shouldDenyNonParticipantSubscription() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/conv-123");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Not a participant"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SUBSCRIBE frame without authenticated user should throw AccessDeniedException")
    void shouldDenyUnauthenticatedSubscription() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/conv-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SEND frame to conversation destination by participant should be authorized")
    void shouldAuthorizeParticipantSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation sampleConv = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of("user-123", "user-456"))
                .participantKey("user-123:user-456")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenReturn(sampleConv);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(conversationService).getConversationAndValidateParticipant("conv-123", "user-123");
    }

    @Test
    @DisplayName("SEND frame to conversation destination by non-participant should throw AccessDeniedException")
    void shouldDenyNonParticipantSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Not a participant"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame without authenticated user should throw AccessDeniedException")
    void shouldDenyUnauthenticatedSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SUBSCRIBE should be denied after the contact relationship is removed")
    void shouldDenySubscriptionAfterContactRemoval() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/conv-123");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND should be denied after the contact relationship is removed")
    void shouldDenySendAfterContactRemoval() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUserPrincipal, null, testUserPrincipal.getAuthorities());
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Chat is only available between accepted contacts"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }
}
