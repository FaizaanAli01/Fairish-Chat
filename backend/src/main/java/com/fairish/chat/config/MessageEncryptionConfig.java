package com.fairish.chat.config;

import com.fairish.chat.encryption.MessageEncryptionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding for message encryption settings. The key itself is provided
 * only through external configuration (environment/secret manager).
 */
@Configuration
@EnableConfigurationProperties(MessageEncryptionProperties.class)
public class MessageEncryptionConfig {
}
