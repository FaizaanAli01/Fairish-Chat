package com.fairish.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTest {

    private CorsConfig configWithOrigins(String origins) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config;
    }

    @Test
    @DisplayName("Should reject an empty CORS origin list")
    void shouldRejectEmptyOrigins() {
        assertThrows(IllegalStateException.class, () -> configWithOrigins("").validateConfiguration());
    }

    @Test
    @DisplayName("Should reject the wildcard origin when credentials are enabled")
    void shouldRejectWildcardOrigin() {
        assertThrows(IllegalStateException.class, () -> configWithOrigins("*").validateConfiguration());
    }

    @Test
    @DisplayName("Should accept explicit CORS origins")
    void shouldAcceptExplicitOrigins() {
        assertDoesNotThrow(() ->
                configWithOrigins("https://app.example.com, https://admin.example.com")
                        .validateConfiguration());
    }
}
