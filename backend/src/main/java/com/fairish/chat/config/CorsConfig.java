package com.fairish.chat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:4173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    /**
     * Fails fast when no origin is configured or when a wildcard is used together
     * with credentialed requests. The prod profile supplies this value from
     * CORS_ALLOWED_ORIGINS with no localhost fallback.
     */
    @PostConstruct
    void validateConfiguration() {
        List<String> origins = parseOrigins();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must contain at least one explicit frontend origin.");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must not contain the wildcard '*' when credentials are enabled.");
        }
    }

    private List<String> parseOrigins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = parseOrigins();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

