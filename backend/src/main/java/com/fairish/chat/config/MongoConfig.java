package com.fairish.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoClientSettingsBuilderCustomizer(
            @Value("${spring.data.mongodb.database:fairish_chat}") String databaseName) {
        return clientSettingsBuilder -> {
            log.info("MongoDB client configuring with target database: [{}]", databaseName);
            clientSettingsBuilder.applyToClusterSettings(cluster ->
                    cluster.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS)
            );
            clientSettingsBuilder.applyToSocketSettings(socket -> {
                socket.connectTimeout(5000, TimeUnit.MILLISECONDS);
                socket.readTimeout(10000, TimeUnit.MILLISECONDS);
            });
        };
    }

    /**
     * Enables Spring's declarative @Transactional support for MongoDB replica sets (e.g. Atlas).
     * Can be disabled via spring.data.mongodb.transaction.enabled=false for standalone local testing.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.mongodb.transaction.enabled", havingValue = "true", matchIfMissing = true)
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        log.info("Registering MongoTransactionManager for declarative transactional boundaries");
        return new MongoTransactionManager(dbFactory);
    }
}
