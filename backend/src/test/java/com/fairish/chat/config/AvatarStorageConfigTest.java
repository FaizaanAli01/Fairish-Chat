package com.fairish.chat.config;

import com.fairish.chat.storage.AvatarStorage;
import com.fairish.chat.storage.DisabledAvatarStorage;
import com.fairish.chat.storage.S3AvatarStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarStorageConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AvatarStorageConfig.class)
            .withPropertyValues(
                    "app.avatar.storage.bucket=test-bucket",
                    "app.avatar.storage.region=us-east-1");

    @Test
    @DisplayName("Disabled avatar storage keeps the context bootable")
    void shouldProvideDisabledStorageWhenFlagIsFalse() {
        runner.withPropertyValues("app.avatar.storage.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AvatarStorage.class))
                            .isInstanceOf(DisabledAvatarStorage.class);
                });
    }

    @Test
    @DisplayName("Enabled avatar storage fails fast when credentials are missing")
    void shouldFailFastWithoutCredentials() {
        runner.withPropertyValues("app.avatar.storage.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Enabled avatar storage wires the S3-compatible adapter")
    void shouldProvideS3StorageWhenConfigured() {
        runner.withPropertyValues(
                        "app.avatar.storage.enabled=true",
                        "app.avatar.storage.access-key=test-key",
                        "app.avatar.storage.secret-key=test-secret",
                        "app.avatar.storage.endpoint=http://localhost:9000",
                        "app.avatar.storage.path-style-access=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AvatarStorage.class))
                            .isInstanceOf(S3AvatarStorage.class);
                });
    }
}
