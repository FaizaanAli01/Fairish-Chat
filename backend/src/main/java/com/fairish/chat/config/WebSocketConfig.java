package com.fairish.chat.config;

import com.fairish.chat.security.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:4173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory simple broker for broadcasting to conversation topics (/topic) and direct user queues (/queue)
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for client-to-server messages routed to @MessageMapping controllers
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific messaging (/user/{username}/queue/...)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        if (origins.length == 0) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must contain at least one exact WebSocket origin.");
        }

        // Standard raw WebSocket endpoint for modern STOMP clients (e.g. @stomp/stompjs)
        // Authentication is strictly performed via STOMP CONNECT headers to avoid token leakage in query strings (SEC-02).
        // Exact origins only: setAllowedOriginPatterns would allow wildcard subdomains.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);

        // SockJS fallback endpoint
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Enforce JWT authentication on CONNECT and participant authorization on SUBSCRIBE and SEND
        registration.interceptors(webSocketAuthInterceptor);
    }
}

