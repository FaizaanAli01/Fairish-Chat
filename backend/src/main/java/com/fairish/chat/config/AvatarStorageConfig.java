package com.fairish.chat.config;

import com.fairish.chat.storage.AvatarStorage;
import com.fairish.chat.storage.DisabledAvatarStorage;
import com.fairish.chat.storage.S3AvatarStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Wires the S3-compatible client used for Cloudflare R2 (production) and
 * MinIO (local development). The same code path serves both providers; only
 * endpoint/region/path-style settings differ.
 */
@Configuration
@EnableConfigurationProperties(AvatarStorageProperties.class)
public class AvatarStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.avatar.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
    public S3Client avatarS3Client(AvatarStorageProperties properties) {
        requireConfigured(properties.getAccessKey(), "AVATAR_STORAGE_ACCESS_KEY");
        requireConfigured(properties.getSecretKey(), "AVATAR_STORAGE_SECRET_KEY");
        requireConfigured(properties.getBucket(), "AVATAR_STORAGE_BUCKET");
        requireConfigured(properties.getRegion(), "AVATAR_STORAGE_REGION");

        log.info("Avatar storage enabled: endpoint={}, region={}, bucket={}, pathStyleAccess={}, createBucket={}, credentials=configured",
                describeEndpoint(properties.getEndpoint()),
                properties.getRegion(),
                properties.getBucket(),
                properties.isPathStyleAccess(),
                properties.isCreateBucket());

        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());

        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }

        return builder.build();
    }

    @Bean
    public AvatarStorage avatarStorage(ObjectProvider<S3Client> s3ClientProvider,
                                       AvatarStorageProperties properties) {
        if (!properties.isEnabled()) {
            log.warn("Avatar storage is DISABLED (app.avatar.storage.enabled=false, set via AVATAR_STORAGE_ENABLED). "
                    + "Avatar upload and retrieval will return HTTP 503. Set AVATAR_STORAGE_ENABLED=true and provide "
                    + "AVATAR_STORAGE_ENDPOINT, AVATAR_STORAGE_ACCESS_KEY, AVATAR_STORAGE_SECRET_KEY and "
                    + "AVATAR_STORAGE_BUCKET to enable it.");
            return new DisabledAvatarStorage();
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new IllegalStateException(
                    "Avatar storage is enabled but the S3 client could not be configured.");
        }
        return new S3AvatarStorage(s3Client, properties);
    }

    private void requireConfigured(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Avatar storage is enabled but " + environmentVariable + " is not configured.");
        }
    }

    /**
     * Logs only scheme, host and port so credentials embedded in an endpoint URL
     * (userinfo) and any path/query are never written to the log.
     */
    private String describeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "(AWS default)";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String host = uri.getHost();
            if (host == null) {
                return "(configured)";
            }
            return uri.getScheme() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException ex) {
            return "(invalid)";
        }
    }
}
