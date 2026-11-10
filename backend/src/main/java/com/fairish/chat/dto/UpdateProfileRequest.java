package com.fairish.chat.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain alphanumeric characters, underscores, dots, and hyphens")
    private String username;

    @Size(max = 500, message = "Profile image URL or data must not exceed 500 characters")
    private String profileImage;

    @Size(max = 250, message = "Bio must not exceed 250 characters")
    private String bio;

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String username, String profileImage, String bio) {
        this.username = username;
        this.profileImage = profileImage;
        this.bio = bio;
    }

    public static UpdateProfileRequestBuilder builder() {
        return new UpdateProfileRequestBuilder();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public static class UpdateProfileRequestBuilder {
        private String username;
        private String profileImage;
        private String bio;

        UpdateProfileRequestBuilder() {
        }

        public UpdateProfileRequestBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UpdateProfileRequestBuilder profileImage(String profileImage) {
            this.profileImage = profileImage;
            return this;
        }

        public UpdateProfileRequestBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public UpdateProfileRequest build() {
            return new UpdateProfileRequest(username, profileImage, bio);
        }
    }
}

