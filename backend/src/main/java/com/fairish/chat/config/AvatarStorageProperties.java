package com.fairish.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Avatar storage configuration. All credentials are supplied through
 * environment variables; no defaults are provided for secrets.
 */
@ConfigurationProperties(prefix = "app.avatar.storage")
public class AvatarStorageProperties {

    private boolean enabled = true;
    private String endpoint;
    private String region = "us-east-1";
    private String bucket = "fairish-avatars";
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    private boolean createBucket = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public boolean isCreateBucket() {
        return createBucket;
    }

    public void setCreateBucket(boolean createBucket) {
        this.createBucket = createBucket;
    }
}
