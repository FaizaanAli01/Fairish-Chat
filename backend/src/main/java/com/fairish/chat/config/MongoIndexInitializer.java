package com.fairish.chat.config;

import com.fairish.chat.model.BlacklistedToken;
import com.fairish.chat.model.ContactRequest;
import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import com.fairish.chat.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ensures all required MongoDB indexes are declared and created on application startup.
 * Fixes DB-01 by providing deterministic schema index guarantees when
 * spring.data.mongodb.auto-index-creation is false.
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mongoMappingContext;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mongoMappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mongoMappingContext = mongoMappingContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking and initializing MongoDB indexes...");
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);

        List<Class<?>> domainClasses = List.of(
                User.class,
                Conversation.class,
                Message.class,
                ContactRequest.class,
                BlacklistedToken.class
        );

        for (Class<?> clazz : domainClasses) {
            try {
                IndexOperations indexOps = mongoTemplate.indexOps(clazz);
                resolver.resolveIndexFor(clazz).forEach(indexOps::ensureIndex);
                log.info("Ensured MongoDB indexes for entity: {}", clazz.getSimpleName());
            } catch (Exception e) {
                log.warn("Could not ensure indexes for entity {}: {}", clazz.getSimpleName(), e.getMessage());
            }
        }
    }
}
