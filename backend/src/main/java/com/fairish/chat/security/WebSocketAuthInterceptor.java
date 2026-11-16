package com.fairish.chat.security;

import com.fairish.chat.exception.TokenBlacklistException;
import com.fairish.chat.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Intercepts inbound STOMP messages to authenticate WebSocket connections via JWT,
 * authorize topic subscriptions (ensuring only conversation participants can subscribe),
 * and authorize message publishing (ensuring only authenticated conversation participants can send).
 *
 * <p>Authentication is established once on CONNECT and then enforced on every
 * subsequent frame: the token must still be present in the session, must not be
 * revoked, and its expiry metadata must be present, well formed, and unexpired.
 * Revocation is checked on every frame so a logout takes effect immediately even
 * while a socket remains open.</p>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private static final String SESSION_ATTR_TOKEN = "authenticatedToken";
    private static final String SESSION_ATTR_EXPIRATION = "tokenExpiration";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ConversationService conversationService;
    private final TokenBlacklistService tokenBlacklistService;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                    CustomUserDetailsService userDetailsService,
                                    ConversationService conversationService,
                                    TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.conversationService = conversationService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        StompCommand command = accessor.getCommand();

        // Heartbeats and other non-STOMP frames carry no command and are not
        // subject to application authorization.
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            return authenticateConnection(message, accessor);
        }

        // Every other frame must belong to a live, authenticated session whose
        // token is still valid and not revoked.
        validateAuthenticatedSession(accessor);

        // Propagate authentication context to the current thread for downstream
        // handlers. It is cleared in afterSendCompletion.
        if (accessor.getUser() instanceof Authentication authentication) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            authorizeSend(accessor);
        }

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        // Inbound channel threads are pooled. Leaving an Authentication bound to
        // the thread would leak one user's context into another user's frame.
        SecurityContextHolder.clearContext();
    }

    /**
     * Re-validates the session token on every non-CONNECT frame. Missing,
     * malformed, expired, or revoked session metadata fails closed.
     */
    private void validateAuthenticatedSession(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("Rejected STOMP command [{}]: no WebSocket session metadata", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("WebSocket session is not authenticated");
        }

        Object tokenValue = sessionAttributes.get(SESSION_ATTR_TOKEN);
        if (!(tokenValue instanceof String token) || !StringUtils.hasText(token)) {
            log.warn("Rejected STOMP command [{}]: missing session token", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("WebSocket session is not authenticated");
        }

        // Revocation must be enforced on every frame, not only on CONNECT, so a
        // logout terminates an already-open socket. Store failures propagate as
        // TokenBlacklistException rather than silently allowing the frame.
        if (isRevoked(token)) {
            log.warn("Rejected STOMP command [{}]: session token has been revoked", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("JWT token has been revoked");
        }

        Object expirationValue = sessionAttributes.get(SESSION_ATTR_EXPIRATION);
        if (!(expirationValue instanceof Number expiration)) {
            log.warn("Rejected STOMP command [{}]: missing or malformed expiry metadata", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("WebSocket session has invalid expiry metadata");
        }

        long expiresAt = expiration.longValue();
        if (expiresAt <= 0L) {
            log.warn("Rejected STOMP command [{}]: invalid expiry metadata", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("WebSocket session has invalid expiry metadata");
        }
        if (System.currentTimeMillis() >= expiresAt) {
            log.warn("Rejected STOMP command [{}]: session token has expired", accessor.getCommand());
            throw new AuthenticationCredentialsNotFoundException("WebSocket session token has expired");
        }
    }

    /**
     * Authenticates the STOMP CONNECT frame using a JWT from the native
     * Authorization/token headers or session attributes.
     */
    private Message<?> authenticateConnection(Message<?> originalMessage, StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        if (!StringUtils.hasText(token)) {
            log.warn("Rejected STOMP CONNECT attempt: missing JWT token in headers");
            throw new AuthenticationCredentialsNotFoundException("Missing JWT authentication token in STOMP headers");
        }

        // Checked before the general try/catch so a revocation-store outage is
        // not downgraded to a generic authentication failure.
        if (isRevoked(token)) {
            log.warn("Rejected STOMP CONNECT attempt: token has been revoked");
            throw new AuthenticationCredentialsNotFoundException("JWT token has been revoked");
        }

        try {
            String username = jwtService.extractUsername(token);

            if (!StringUtils.hasText(username)) {
                throw new AuthenticationCredentialsNotFoundException("Invalid JWT token");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtService.validateToken(token, userDetails)) {
                log.warn("Rejected STOMP CONNECT attempt: invalid or expired JWT token");
                throw new AuthenticationCredentialsNotFoundException("Invalid or expired JWT token");
            }

            Date expiration = jwtService.extractExpiration(token);
            if (expiration == null) {
                log.warn("Rejected STOMP CONNECT attempt: token has no expiration claim");
                throw new AuthenticationCredentialsNotFoundException("Invalid JWT token: missing expiration");
            }
            if (expiration.before(new Date())) {
                log.warn("Rejected STOMP CONNECT attempt: token has expired");
                throw new AuthenticationCredentialsNotFoundException("Invalid or expired JWT token");
            }

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                log.warn("Rejected STOMP CONNECT attempt: WebSocket session attributes unavailable");
                throw new AuthenticationCredentialsNotFoundException("WebSocket session is not available");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            sessionAttributes.put(SESSION_ATTR_TOKEN, token);
            sessionAttributes.put(SESSION_ATTR_EXPIRATION, expiration.getTime());

            if (accessor.isMutable()) {
                accessor.setUser(authentication);
                log.info("WebSocket client authenticated successfully");
                return originalMessage;
            }

            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            mutableAccessor.copyHeaders(accessor.getMessageHeaders());
            mutableAccessor.setUser(authentication);
            mutableAccessor.setLeaveMutable(true);
            log.info("WebSocket client authenticated successfully");
            return MessageBuilder.createMessage(originalMessage.getPayload(), mutableAccessor.getMessageHeaders());
        } catch (AuthenticationCredentialsNotFoundException | TokenBlacklistException ex) {
            throw ex;
        } catch (Exception ex) {
            // Never include parser/library details in the client-visible reason.
            log.warn("Failed to authenticate STOMP connection: {}", ex.getClass().getSimpleName());
            throw new AuthenticationCredentialsNotFoundException("Authentication failed", ex);
        }
    }

    /**
     * Authorizes STOMP SUBSCRIBE frames, ensuring users can only subscribe to conversations they belong to
     * or their private user queues.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Subscription destination cannot be blank");
        }

        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("Rejected subscription to [{}] - user is not authenticated", destination);
            throw new AccessDeniedException("User must be authenticated to subscribe to destinations");
        }

        String userId = principal.getId();

        // 1. Conversation topic: /topic/conversations/{conversationId}
        if (destination.startsWith("/topic/conversations/")) {
            String conversationId = destination.substring("/topic/conversations/".length()).trim();
            if (StringUtils.hasText(conversationId)) {
                try {
                    // Validates existence and that the user is an accepted participant.
                    conversationService.getConversationAndValidateParticipant(conversationId, userId);
                    log.debug("Authorized subscription to conversation topic [{}]", destination);
                } catch (Exception ex) {
                    log.warn("Denied subscription to [{}]: {}", destination, ex.getMessage());
                    throw new AccessDeniedException("Access denied: You are not a participant in this conversation");
                }
            } else {
                throw new AccessDeniedException("Invalid conversation subscription destination");
            }
        } else if (destination.equals("/user/queue/unread") || destination.equals("/user/queue/receipts")) {
            // 2. Private queues resolved by Spring UserDestinationMessageHandler
            log.debug("Authorized subscription to private queue [{}]", destination);
        } else {
            // Disallow subscriptions to arbitrary broker destinations
            log.warn("Denied subscription to unauthorized destination [{}]", destination);
            throw new AccessDeniedException("Access denied: Unauthorized subscription destination");
        }
    }

    /**
     * Authorizes STOMP SEND frames, ensuring users can only publish to valid application endpoints (/app/...)
     * and preventing direct message injection into broker destinations (/topic/..., /queue/...).
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("Rejected SEND - user is not authenticated");
            throw new AccessDeniedException("User must be authenticated to send messages");
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Destination cannot be blank");
        }

        // Direct publishing to broker topics or queues is strictly prohibited
        if (destination.startsWith("/topic/") || destination.startsWith("/queue/")) {
            log.warn("Blocked direct client SEND to broker destination [{}]", destination);
            throw new AccessDeniedException("Clients may not publish directly to broker destinations");
        }

        // Only allow application destinations
        if (!destination.startsWith("/app/")) {
            log.warn("Blocked SEND to non-application destination [{}]", destination);
            throw new AccessDeniedException("Invalid message destination: must start with /app/");
        }

        if (destination.startsWith("/app/conversations/")) {
            String remainder = destination.substring("/app/conversations/".length());
            int slashIdx = remainder.indexOf('/');
            String conversationId = slashIdx > 0 ? remainder.substring(0, slashIdx) : remainder;
            if (StringUtils.hasText(conversationId)) {
                try {
                    conversationService.getConversationAndValidateParticipant(conversationId, principal.getId());
                    log.debug("Authorized SEND to [{}]", destination);
                } catch (Exception ex) {
                    log.warn("Denied SEND to [{}]: {}", destination, ex.getMessage());
                    throw new AccessDeniedException("Access denied: You are not a participant in this conversation");
                }
            } else {
                throw new AccessDeniedException("Invalid conversation destination");
            }
        } else if (!destination.equals("/app/chat.sendMessage")) {
            log.warn("Blocked SEND to unrecognized application destination [{}]", destination);
            throw new AccessDeniedException("Unrecognized message destination");
        }
    }

    /**
     * Fails closed when the revocation store is unavailable.
     */
    private boolean isRevoked(String token) {
        try {
            return tokenBlacklistService.isBlacklisted(token);
        } catch (TokenBlacklistException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Token revocation store unavailable while validating WebSocket frame");
            throw new TokenBlacklistException("Token revocation store unavailable", ex);
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Try native header "Authorization" (Bearer <token>)
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                return header.substring(7).trim();
            }
            if (StringUtils.hasText(header)) {
                return header.trim();
            }
        }

        // 2. Try native header "token"
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            String token = tokenHeaders.get(0);
            if (StringUtils.hasText(token)) {
                return token.trim();
            }
        }

        // 3. Try session attributes if set by a handshake interceptor
        if (accessor.getSessionAttributes() != null && accessor.getSessionAttributes().containsKey("token")) {
            Object tokenObj = accessor.getSessionAttributes().get("token");
            if (tokenObj != null) {
                return tokenObj.toString();
            }
        }

        return null;
    }
}
