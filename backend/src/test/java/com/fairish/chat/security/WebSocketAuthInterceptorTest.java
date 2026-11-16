package com.fairish.chat.security;

import com.fairish.chat.exception.TokenBlacklistException;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    private static final String TOKEN = "valid-jwt-token";
    private static final long FUTURE_EXPIRATION = System.currentTimeMillis() + 60_000L;

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

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
                List.of()
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // CONNECT
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONNECT frame with valid Bearer token should authenticate user successfully")
    void shouldAuthenticateValidBearerTokenOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        Message<byte[]> message = message(accessor);

        when(jwtService.extractUsername(TOKEN)).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken(TOKEN, testUserPrincipal)).thenReturn(true);
        when(jwtService.extractExpiration(TOKEN)).thenReturn(new Date(FUTURE_EXPIRATION));

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("alice", resultAccessor.getUser().getName());
        assertTrue(resultAccessor.getUser() instanceof Authentication);
        assertEquals(TOKEN, resultAccessor.getSessionAttributes().get("authenticatedToken"));
        assertEquals(FUTURE_EXPIRATION, resultAccessor.getSessionAttributes().get("tokenExpiration"));
    }

    @Test
    @DisplayName("CONNECT frame with valid 'token' header should authenticate user successfully")
    void shouldAuthenticateValidTokenHeaderOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("token", TOKEN);
        Message<byte[]> message = message(accessor);

        when(jwtService.extractUsername(TOKEN)).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken(TOKEN, testUserPrincipal)).thenReturn(true);
        when(jwtService.extractExpiration(TOKEN)).thenReturn(new Date(FUTURE_EXPIRATION));

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("alice", resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT frame without token should throw AuthenticationCredentialsNotFoundException")
    void shouldRejectMissingTokenOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        Message<byte[]> message = message(accessor);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT frame with invalid token should throw AuthenticationCredentialsNotFoundException")
    void shouldRejectInvalidTokenOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("Authorization", "Bearer invalid-token");
        Message<byte[]> message = message(accessor);

        when(jwtService.extractUsername("invalid-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken("invalid-token", testUserPrincipal)).thenReturn(false);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("CONNECT frame without an expiration claim must be rejected")
    void shouldRejectTokenWithoutExpirationOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        Message<byte[]> message = message(accessor);

        when(jwtService.extractUsername(TOKEN)).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(testUserPrincipal);
        when(jwtService.validateToken(TOKEN, testUserPrincipal)).thenReturn(true);
        when(jwtService.extractExpiration(TOKEN)).thenReturn(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("CONNECT frame with blacklisted token must be rejected (SEC-05)")
    void shouldDenyBlacklistedTokenOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("Authorization", "Bearer blacklisted-jwt-token");
        Message<byte[]> message = message(accessor);

        when(tokenBlacklistService.isBlacklisted("blacklisted-jwt-token")).thenReturn(true);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT must fail closed when the revocation store is unavailable")
    void shouldFailClosedWhenRevocationStoreUnavailableOnConnect() {
        StompHeaderAccessor accessor = connectAccessor();
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        Message<byte[]> message = message(accessor);

        when(tokenBlacklistService.isBlacklisted(TOKEN))
                .thenThrow(new TokenBlacklistException("store down", new RuntimeException("boom")));

        assertThrows(TokenBlacklistException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(jwtService, userDetailsService);
    }

    // ------------------------------------------------------------------
    // SUBSCRIBE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SUBSCRIBE frame to conversation topic by participant should be authorized")
    void shouldAuthorizeParticipantSubscription() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/conv-123");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

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
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/conv-123");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Not a participant"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SUBSCRIBE frame without authenticated user should throw AccessDeniedException")
    void shouldDenyUnauthenticatedSubscription() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/conv-123");
        Message<byte[]> message = message(accessor);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SUBSCRIBE frame to non-whitelisted destination must be rejected (SEC-01)")
    void shouldDenyUnauthorizedSubscriptionDestination() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/secret-alerts");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SUBSCRIBE frame with a revoked session token must be rejected")
    void shouldDenySubscriptionWhenSessionTokenRevoked() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/conv-123");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(true);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    // ------------------------------------------------------------------
    // SEND
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SEND frame to conversation destination by participant should be authorized")
    void shouldAuthorizeParticipantSend() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

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
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        when(conversationService.getConversationAndValidateParticipant("conv-123", "user-123"))
                .thenThrow(new AccessDeniedException("Not a participant"));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame without authenticated user should throw AccessDeniedException")
    void shouldDenyUnauthenticatedSend() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        Message<byte[]> message = message(accessor);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SEND frame directly to broker topic destination must be rejected (SEC-01)")
    void shouldDenyDirectSendToBrokerTopic() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/topic/conversations/conv-123");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        assertEquals("Clients may not publish directly to broker destinations", ex.getMessage());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SEND frame directly to broker queue destination must be rejected")
    void shouldDenyDirectSendToBrokerQueue() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/queue/private-user-queue");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame with expired session token must be rejected (SEC-04)")
    void shouldDenySendWhenSessionExpired() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        accessor.setSessionAttributes(new HashMap<>(Map.<String, Object>of(
                "authenticatedToken", TOKEN,
                "tokenExpiration", System.currentTimeMillis() - 60_000L)));
        Message<byte[]> message = message(accessor);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame with a revoked session token must be rejected")
    void shouldDenySendWhenSessionTokenRevoked() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(true);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("SEND frame with missing session token must be rejected")
    void shouldDenySendWhenSessionTokenMissing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        accessor.setSessionAttributes(new HashMap<>(Map.<String, Object>of(
                "tokenExpiration", FUTURE_EXPIRATION)));
        Message<byte[]> message = message(accessor);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame with malformed expiry metadata must be rejected")
    void shouldDenySendWhenExpiryMalformed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        accessor.setSessionAttributes(new HashMap<>(Map.<String, Object>of(
                "authenticatedToken", TOKEN,
                "tokenExpiration", "not-a-number")));
        Message<byte[]> message = message(accessor);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND frame with missing expiry metadata must be rejected")
    void shouldDenySendWhenExpiryMissing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        accessor.setSessionAttributes(new HashMap<>(Map.<String, Object>of(
                "authenticatedToken", TOKEN)));
        Message<byte[]> message = message(accessor);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    @Test
    @DisplayName("SEND must fail closed when the revocation store is unavailable")
    void shouldFailClosedWhenRevocationStoreUnavailableOnSend() {
        StompHeaderAccessor accessor = sessionAccessor(StompCommand.SEND);
        accessor.setDestination("/app/conversations/conv-123/sendMessage");
        accessor.setUser(authentication());
        Message<byte[]> message = message(accessor);

        when(tokenBlacklistService.isBlacklisted(TOKEN))
                .thenThrow(new TokenBlacklistException("store down", new RuntimeException("boom")));

        assertThrows(TokenBlacklistException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("afterSendCompletion must clear the thread-local security context")
    void shouldClearSecurityContextAfterSendCompletion() {
        SecurityContextHolder.getContext().setAuthentication(authentication());

        interceptor.afterSendCompletion(null, messageChannel, true, null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StompHeaderAccessor connectAccessor() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        return accessor;
    }

    private StompHeaderAccessor sessionAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>(Map.<String, Object>of(
                "authenticatedToken", TOKEN,
                "tokenExpiration", FUTURE_EXPIRATION)));
        return accessor;
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(
                testUserPrincipal, null, testUserPrincipal.getAuthorities());
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
