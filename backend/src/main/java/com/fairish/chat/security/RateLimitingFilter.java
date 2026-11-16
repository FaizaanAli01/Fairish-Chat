package com.fairish.chat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that enforces rate limits on authentication endpoints before processing credentials.
 * Protects against credential stuffing, brute-forcing, and resource exhaustion (SEC-03).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /**
     * Authentication payloads are tiny. Rejecting oversized bodies before the
     * request reaches Jackson prevents memory/CPU exhaustion from a single
     * unauthenticated request.
     */
    static final long MAX_AUTH_BODY_BYTES = 16 * 1024;

    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;

    public RateLimitingFilter(RateLimiterService rateLimiterService,
                              ClientIpResolver clientIpResolver) {
        this.rateLimiterService = rateLimiterService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = requestPath(request);
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && isAuthPath(path)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_AUTH_BODY_BYTES) {
                sendPayloadTooLargeError(response);
                return;
            }

            String clientIp = clientIpResolver.resolve(request);

            if ("/api/auth/login".equals(path)) {
                if (!rateLimiterService.allowLogin(clientIp)) {
                    sendRateLimitError(response);
                    return;
                }
            } else if ("/api/auth/register".equals(path)) {
                if (!rateLimiterService.allowRegister(clientIp)) {
                    sendRateLimitError(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthPath(String path) {
        return "/api/auth/login".equals(path) || "/api/auth/register".equals(path);
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private void sendRateLimitError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
    }

    private void sendPayloadTooLargeError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Request body is too large.\"}");
    }
}
