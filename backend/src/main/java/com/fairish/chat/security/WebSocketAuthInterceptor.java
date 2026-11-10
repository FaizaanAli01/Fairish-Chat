package com.fairish.chat.security;

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

import java.util.List;

/**
 * Intercepts inbound STOMP messages to authenticate WebSocket connections via JWT,
 * authorize topic subscriptions (ensuring only conversation participants can subscribe),
 * and authorize message publishing (ensuring only authenticated conversation participants can send).
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ConversationService conversationService;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                    CustomUserDetailsService userDetailsService,
                                    ConversationService conversationService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.conversationService = conversationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            return authenticateConnection(message, accessor);
        }

        // Propagate authentication context to current thread if user is present on session
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

    /**
     * Authenticates the STOMP CONNECT frame using JWT from the Authorization header or session attributes.
     */
    private Message<?> authenticateConnection(Message<?> originalMessage, StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        if (!StringUtils.hasText(token)) {
            log.warn("Rejected STOMP CONNECT attempt: missing JWT token in headers");
            throw new AuthenticationCredentialsNotFoundException("Missing JWT authentication token in STOMP headers");
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username == null) {
                throw new AuthenticationCredentialsNotFoundException("Invalid JWT token: username could not be extracted");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtService.validateToken(token, userDetails)) {
                log.warn("Rejected STOMP CONNECT attempt: invalid or expired JWT token for user [{}]", username);
                throw new AuthenticationCredentialsNotFoundException("Invalid or expired JWT token");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (accessor.isMutable()) {
                accessor.setUser(authentication);
                log.info("WebSocket client authenticated successfully: [{}]", username);
                return originalMessage;
            } else {
                StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
                mutableAccessor.copyHeaders(accessor.getMessageHeaders());
                mutableAccessor.setUser(authentication);
                mutableAccessor.setLeaveMutable(true);
                log.info("WebSocket client authenticated successfully: [{}]", username);
                return MessageBuilder.createMessage(originalMessage.getPayload(), mutableAccessor.getMessageHeaders());
            }
        } catch (AuthenticationCredentialsNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to authenticate STOMP connection: {}", ex.getMessage());
            throw new AuthenticationCredentialsNotFoundException("Authentication failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Authorizes STOMP SUBSCRIBE frames, ensuring users can only subscribe to conversations they belong to.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }

        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("Rejected subscription to [{}] - user is not authenticated", destination);
            throw new AccessDeniedException("User must be authenticated to subscribe to destinations");
        }

        String userId = principal.getId();

        // Check if destination is a conversation topic: /topic/conversations/{conversationId}
        if (destination.startsWith("/topic/conversations/")) {
            String conversationId = destination.substring("/topic/conversations/".length()).trim();
            if (StringUtils.hasText(conversationId)) {
                try {
                    // Validates existence and that the user is a participant. Throws AccessDeniedException if not.
                    conversationService.getConversationAndValidateParticipant(conversationId, userId);
                    log.debug("User [{}] authorized to subscribe to conversation topic [{}]", principal.getUsername(), destination);
                } catch (Exception ex) {
                    log.warn("Denied subscription to [{}] for user [{}]: {}", destination, principal.getUsername(), ex.getMessage());
                    throw new AccessDeniedException("Access denied: You are not a participant in this conversation");
                }
            }
        }
    }

    /**
     * Authorizes STOMP SEND frames, ensuring users can only publish to destinations they are authorized for.
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("Rejected SEND - user is not authenticated");
            throw new AccessDeniedException("User must be authenticated to send messages");
        }

        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/app/conversations/")) {
            String remainder = destination.substring("/app/conversations/".length());
            int slashIdx = remainder.indexOf('/');
            String conversationId = slashIdx > 0 ? remainder.substring(0, slashIdx) : remainder;
            if (StringUtils.hasText(conversationId)) {
                try {
                    conversationService.getConversationAndValidateParticipant(conversationId, principal.getId());
                    log.debug("User [{}] authorized to send to [{}]", principal.getUsername(), destination);
                } catch (Exception ex) {
                    log.warn("Denied SEND to [{}] for user [{}]: {}", destination, principal.getUsername(), ex.getMessage());
                    throw new AccessDeniedException("Access denied: You are not a participant in this conversation");
                }
            }
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

        // 3. Try session attributes if set by handshake interceptor
        if (accessor.getSessionAttributes() != null && accessor.getSessionAttributes().containsKey("token")) {
            Object tokenObj = accessor.getSessionAttributes().get("token");
            if (tokenObj != null) {
                return tokenObj.toString();
            }
        }

        return null;
    }
}
