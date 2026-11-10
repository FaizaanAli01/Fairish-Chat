package com.fairish.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from backend/.env or .env during local development.
 * Registered as a Spring Boot EnvironmentPostProcessor to ensure variables are available
 * when application.yml property placeholders are resolved.
 *
 * Follows standard 12-factor configuration order:
 * 1. Command-line arguments
 * 2. System properties (-D)
 * 3. OS environment variables
 * 4. .env properties
 * 5. application.yml defaults
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    public static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    private static volatile boolean loaded = false;
    private static final Map<String, Object> cachedProperties = new HashMap<>();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = loadDotenvMap();
        if (!properties.isEmpty()) {
            if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            } else {
                environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            }
        }
    }

    /**
     * Helper to load .env into System properties before Spring Boot boots up.
     */
    public static void loadDotenvLocally() {
        loadDotenvMap();
    }

    private static synchronized Map<String, Object> loadDotenvMap() {
        if (loaded) {
            return cachedProperties;
        }

        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists() || !envFile.canRead()) {
            log.debug("No local .env file found; continuing with system environment and application.yml defaults");
            loaded = true;
            return cachedProperties;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(envFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Strip surrounding single or double quotes
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }

                cachedProperties.put(key, value);

                // Set as system property if not already set by JVM or OS environment
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
            log.info("Loaded {} environment variable(s) from .env at [{}]", cachedProperties.size(), envFile.getAbsolutePath());
        } catch (Exception ex) {
            log.warn("Failed to read .env file at [{}]: {}", envFile.getAbsolutePath(), ex.getMessage());
        }

        loaded = true;
        return cachedProperties;
    }

    private static File findDotenvFile() {
        String userDir = System.getProperty("user.dir", ".");
        Path[] candidatePaths = new Path[] {
                Paths.get("backend", ".env"),
                Paths.get(".env"),
                Paths.get("..", ".env"),
                Paths.get("..", "backend", ".env"),
                Paths.get(userDir, "backend", ".env"),
                Paths.get(userDir, ".env")
        };

        for (Path path : candidatePaths) {
            File f = path.toFile();
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Run before ConfigDataEnvironmentPostProcessor so properties are available for application.yml placeholders
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
