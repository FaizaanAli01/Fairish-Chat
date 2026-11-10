package com.fairish.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
