package com.fairish.chat;

import com.fairish.chat.config.DotenvEnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FairishChatApplication {

    public static void main(String[] args) {
        DotenvEnvironmentPostProcessor.loadDotenvLocally();
        SpringApplication.run(FairishChatApplication.class, args);
    }
}
