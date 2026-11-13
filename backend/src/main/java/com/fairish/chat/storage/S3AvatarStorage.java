package com.fairish.chat.storage;

import com.fairish.chat.config.AvatarStorageProperties;
import com.fairish.chat.exception.AvatarStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * S3-compatible adapter. Works against Cloudflare R2 and MinIO without
 * provider-specific branches.
 *
 * <p>Objects live in a private bucket and are only ever read by the backend
 * proxy. No presigned URLs or public object URLs are produced.</p>
 */
public class S3AvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(S3AvatarStorage.class);

    private final S3Client s3Client;
    private final AvatarStorageProperties properties;
    private volatile boolean bucketReady;

    public S3AvatarStorage(S3Client s3Client, AvatarStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new AvatarStorageException("Failed to store avatar object", ex);
        }
    }

    @Override
    public Optional<StoredAvatar> get(String key) {
        try {
            var response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return Optional.of(new StoredAvatar(
                    response.asByteArray(),
                    response.response().contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw new AvatarStorageException("Failed to read avatar object", ex);
        } catch (SdkException ex) {
            throw new AvatarStorageException("Failed to read avatar object", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (SdkException ex) {
            throw new AvatarStorageException("Failed to delete avatar object", ex);
        }
    }

    /**
     * Lazily creates the bucket for local MinIO. Disabled in production, where
     * the R2 bucket is provisioned out of band.
     */
    private void ensureBucket() {
        if (!properties.isCreateBucket() || bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                s3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(properties.getBucket())
                        .build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    try {
                        s3Client.createBucket(CreateBucketRequest.builder()
                                .bucket(properties.getBucket())
                                .build());
                    } catch (S3Exception createEx) {
                        if (createEx.statusCode() != 409) {
                            throw new AvatarStorageException("Failed to create avatar bucket", createEx);
                        }
                    }
                } else {
                    throw new AvatarStorageException("Failed to inspect avatar bucket", ex);
                }
            } catch (SdkException ex) {
                throw new AvatarStorageException("Failed to inspect avatar bucket", ex);
            }
            bucketReady = true;
            log.info("Avatar storage bucket is ready");
        }
    }
}
