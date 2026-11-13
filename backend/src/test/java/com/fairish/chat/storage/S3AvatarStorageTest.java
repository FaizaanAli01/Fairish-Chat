package com.fairish.chat.storage;

import com.fairish.chat.config.AvatarStorageProperties;
import com.fairish.chat.exception.AvatarStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3AvatarStorageTest {

    @Mock
    private S3Client s3Client;

    private AvatarStorageProperties properties;
    private S3AvatarStorage storage;

    @BeforeEach
    void setUp() {
        properties = new AvatarStorageProperties();
        properties.setBucket("test-bucket");
        properties.setCreateBucket(false);
        storage = new S3AvatarStorage(s3Client, properties);
    }

    @Test
    @DisplayName("put writes the object to the configured private bucket")
    void shouldPutObject() {
        byte[] content = {1, 2, 3};

        storage.put("avatars/key.png", content, "image/png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertEquals("test-bucket", request.bucket());
        assertEquals("avatars/key.png", request.key());
        assertEquals("image/png", request.contentType());
        assertEquals(3L, request.contentLength());
    }

    @Test
    @DisplayName("put wraps SDK failures in a storage exception without leaking details")
    void shouldWrapPutFailure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("internal-host:9000").build());

        AvatarStorageException ex = assertThrows(AvatarStorageException.class,
                () -> storage.put("avatars/key.png", new byte[]{1}, "image/png"));

        assertEquals("Failed to store avatar object", ex.getMessage());
    }

    @Test
    @DisplayName("get returns object bytes and the stored content type")
    void shouldGetObject() {
        byte[] content = {4, 5, 6};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().contentType("image/jpeg").build(), content));

        Optional<StoredAvatar> result = storage.get("avatars/key.jpg");

        assertTrue(result.isPresent());
        assertArrayEquals(content, result.get().content());
        assertEquals("image/jpeg", result.get().contentType());

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("avatars/key.jpg", captor.getValue().key());
    }

    @Test
    @DisplayName("get returns empty when the object does not exist")
    void shouldReturnEmptyForMissingKey() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertTrue(storage.get("avatars/missing.png").isEmpty());
    }

    @Test
    @DisplayName("get returns empty for a 404 response")
    void shouldReturnEmptyFor404() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertTrue(storage.get("avatars/missing.png").isEmpty());
    }

    @Test
    @DisplayName("get wraps non-404 S3 failures")
    void shouldWrapGetFailure() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        AvatarStorageException ex = assertThrows(AvatarStorageException.class,
                () -> storage.get("avatars/key.png"));

        assertEquals("Failed to read avatar object", ex.getMessage());
    }

    @Test
    @DisplayName("get wraps generic SDK failures")
    void shouldWrapGenericGetFailure() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("network").build());

        assertThrows(AvatarStorageException.class, () -> storage.get("avatars/key.png"));
    }

    @Test
    @DisplayName("delete removes the object from the private bucket")
    void shouldDeleteObject() {
        storage.delete("avatars/key.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("avatars/key.png", captor.getValue().key());
    }

    @Test
    @DisplayName("delete wraps SDK failures")
    void shouldWrapDeleteFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        AvatarStorageException ex = assertThrows(AvatarStorageException.class,
                () -> storage.delete("avatars/key.png"));

        assertEquals("Failed to delete avatar object", ex.getMessage());
    }

    @Test
    @DisplayName("A missing bucket is created lazily when enabled")
    void shouldCreateMissingBucket() {
        properties.setCreateBucket(true);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        storage.put("avatars/key.png", new byte[]{1}, "image/png");

        ArgumentCaptor<CreateBucketRequest> captor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
    }

    @Test
    @DisplayName("Bucket inspection happens once and is cached")
    void shouldCacheBucketReadiness() {
        properties.setCreateBucket(true);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        storage.put("avatars/one.png", new byte[]{1}, "image/png");
        storage.put("avatars/two.png", new byte[]{2}, "image/png");

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("An existing bucket is not re-created")
    void shouldNotCreateExistingBucket() {
        properties.setCreateBucket(true);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        storage.put("avatars/key.png", new byte[]{1}, "image/png");

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("A concurrent bucket creation (409) is tolerated")
    void shouldTolerateBucketAlreadyExists() {
        properties.setCreateBucket(true);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(409).build());

        storage.put("avatars/key.png", new byte[]{1}, "image/png");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("A bucket inspection failure is surfaced as a storage exception")
    void shouldWrapBucketInspectionFailure() {
        properties.setCreateBucket(true);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());

        assertThrows(AvatarStorageException.class,
                () -> storage.put("avatars/key.png", new byte[]{1}, "image/png"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
