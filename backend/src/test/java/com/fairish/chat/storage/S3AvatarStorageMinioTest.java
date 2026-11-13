package com.fairish.chat.storage;

import com.fairish.chat.config.AvatarStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the S3 adapter against a real MinIO server.
 *
 * <p>Automatically skipped when Docker is not available, so the regular test
 * suite remains runnable on machines without a container runtime.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class S3AvatarStorageMinioTest {

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "fairish-avatars-test";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2024-06-13T22-53-53Z"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server /data")
            .waitingFor(Wait.forHttp("/minio/health/ready")
                    .forPort(9000)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @Test
    @DisplayName("MinIO round-trip: create bucket, put, get, delete")
    void shouldRoundTripAgainstMinio() {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setEndpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        properties.setRegion("us-east-1");
        properties.setBucket(BUCKET);
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setPathStyleAccess(true);
        properties.setCreateBucket(true);

        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        try {
            S3AvatarStorage storage = new S3AvatarStorage(client, properties);
            byte[] content = {1, 2, 3, 4, 5};

            storage.put("avatars/integration.png", content, "image/png");

            Optional<StoredAvatar> loaded = storage.get("avatars/integration.png");
            assertTrue(loaded.isPresent());
            assertArrayEquals(content, loaded.get().content());
            assertEquals("image/png", loaded.get().contentType());

            storage.delete("avatars/integration.png");
            assertTrue(storage.get("avatars/integration.png").isEmpty());
        } finally {
            client.close();
        }
    }
}
