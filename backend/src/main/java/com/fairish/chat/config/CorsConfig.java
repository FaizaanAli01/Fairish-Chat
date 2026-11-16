package com.fairish.chat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:4173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    /**
     * Fails fast when no origin is configured or when an origin is not an exact
     * HTTP(S) origin. Wildcards and path-bearing URLs are rejected so a
     * misconfiguration cannot silently broaden access.
     */
    @PostConstruct
    void validateConfiguration() {
        List<String> origins = parseOrigins();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must contain at least one explicit frontend origin.");
        }
        for (String origin : origins) {
            validateOrigin(origin);
        }
    }

    private void validateOrigin(String origin) {
        if (origin.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must contain exact origins, not wildcard patterns: " + origin);
        }

        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS contains an invalid origin: " + origin, ex);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS entries must use http or https: " + origin);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS entry has no host: " + origin);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS entries must not contain user info: " + origin);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS entries must not contain a query or fragment: " + origin);
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS entries must be origins without a path: " + origin);
        }
    }

    private List<String> parseOrigins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.replaceAll("/+$", ""))
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = parseOrigins();
        // Exact origins only. setAllowedOriginPatterns would permit wildcard
        // subdomains and is intentionally not used here.
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        // Authentication is Bearer-token only and the API never issues cookies,
        // so credentialed cross-origin requests are unnecessary. Keeping this
        // false means a browser will not attach cookies to cross-origin calls
        // even if another application on the same site sets them.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
