package com.fairish.chat.service;

/**
 * Formats accepted for avatar uploads in V1.0.
 */
public enum AvatarFormat {
    JPEG("jpg", "image/jpeg", "jpg"),
    PNG("png", "image/png", "png");

    private final String extension;
    private final String contentType;
    private final String imageIoName;

    AvatarFormat(String extension, String contentType, String imageIoName) {
        this.extension = extension;
        this.contentType = contentType;
        this.imageIoName = imageIoName;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getImageIoName() {
        return imageIoName;
    }
}
