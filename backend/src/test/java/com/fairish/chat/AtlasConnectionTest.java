package com.fairish.chat;

import com.fairish.chat.config.DotenvEnvironmentPostProcessor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;

public class AtlasConnectionTest {

    @Test
    void testAtlasConnection() {
        DotenvEnvironmentPostProcessor.loadDotenvLocally();
        String uri = System.getProperty("MONGODB_URI");
        System.out.println("Testing URI: " + (uri != null ? uri.replaceAll(":[^:@]+@", ":****@") : "null"));
        try (MongoClient client = MongoClients.create(uri)) {
            Document ping = client.getDatabase("fairish_chat").runCommand(new Document("ping", 1));
            System.out.println("Atlas ping result: " + ping.toJson());
        } catch (Exception e) {
            System.err.println("Atlas connection error: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }
}
