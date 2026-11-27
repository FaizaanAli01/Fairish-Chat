package com.fairish.chat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return "GET".equalsIgnoreCase(request.getMethod()) && "/api/health".equals(path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                boolean revoked;
                try {
                    revoked = tokenBlacklistService.isBlacklisted(jwt);
                } catch (RuntimeException ex) {
                    // A revocation-store outage must not be reported as a normal
                    // 401: the client needs to know the server cannot make an
                    // authorization decision right now.
                    SecurityContextHolder.clearContext();
                    log.error("Token revocation store unavailable; refusing request");
                    sendServiceUnavailable(response);
                    return;
                }

                if (revoked) {
                    log.debug("Rejected request with revoked JWT token");
                    SecurityContextHolder.clearContext();
                } else {
                    String username = jwtService.extractUsername(jwt);

                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (jwtService.validateToken(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            userDetails.getAuthorities()
                                    );
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // Fail closed: an invalid token must never leave the request authenticated.
            SecurityContextHolder.clearContext();
            log.error("Could not set user authentication in security context: {}", ex.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    private void sendServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":503,\"error\":\"Service Unavailable\","
                        + "\"message\":\"Authentication is temporarily unavailable. Please try again later.\"}");
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }

        // The auth-scheme is case-insensitive (RFC 7235), and the token must be
        // a single non-empty value. Anything else is treated as unauthenticated
        // rather than being interpreted loosely.
        String[] parts = bearerToken.trim().split("\\s+", 2);
        if (parts.length == 2
                && "Bearer".equalsIgnoreCase(parts[0])
                && StringUtils.hasText(parts[1])) {
            return parts[1].trim();
        }
        return null;
    }
}
