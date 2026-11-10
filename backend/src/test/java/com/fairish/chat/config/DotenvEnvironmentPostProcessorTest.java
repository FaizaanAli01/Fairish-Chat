package com.fairish.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DotenvEnvironmentPostProcessorTest {

    @Test
    @DisplayName("DotenvEnvironmentPostProcessor should execute before ConfigDataEnvironmentPostProcessor")
    void testExecutionOrder() {
        DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
        assertEquals(ConfigDataEnvironmentPostProcessor.ORDER - 1, processor.getOrder());
    }

    @Test
    @DisplayName("DotenvEnvironmentPostProcessor should process environment gracefully and load dotenv properties")
    void testPostProcessEnvironment() {
        DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
        ConfigurableEnvironment environment = new StandardEnvironment();
        SpringApplication application = new SpringApplication(DotenvEnvironmentPostProcessorTest.class);

        assertDoesNotThrow(() -> processor.postProcessEnvironment(environment, application));

        PropertySource<?> dotenvSource = environment.getPropertySources().get(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
        assertNotNull(dotenvSource, "dotenvProperties PropertySource should be registered in Environment");

        String mongoUri = (String) dotenvSource.getProperty("MONGODB_URI");
        assertNotNull(mongoUri, "MONGODB_URI should be loaded from .env");
        assertTrue(mongoUri.startsWith("mongodb+srv://"), "MONGODB_URI should be the Atlas URI from .env");
        assertFalse(mongoUri.contains("localhost"), "MONGODB_URI should not be localhost");

        assertEquals("fairish_chat", dotenvSource.getProperty("MONGODB_DATABASE"));
        assertEquals("8080", dotenvSource.getProperty("PORT"));
    }
}
