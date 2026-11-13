package com.fairish.chat.dto;

import com.fairish.chat.model.User;

import java.time.Instant;

public class UserResponse {

    private String id;
    private String username;
    private String email;
    private String profileImage;
    private boolean hasAvatar;
    private Instant avatarUpdatedAt;
    private String bio;
    private boolean online;
    private Instant lastSeen;
    private Instant createdAt;
    private Instant updatedAt;

    public UserResponse() {
    }

    public UserResponse(String id, String username, String email, String profileImage,
                        boolean hasAvatar, Instant avatarUpdatedAt, String bio,
                        boolean online, Instant lastSeen, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.profileImage = profileImage;
        this.hasAvatar = hasAvatar;
        this.avatarUpdatedAt = avatarUpdatedAt;
        this.bio = bio;
        this.online = online;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserResponse fromUser(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .hasAvatar(user.hasAvatar())
                .avatarUpdatedAt(user.getAvatarUpdatedAt())
                .bio(user.getBio())
                .online(user.isOnline())
                .lastSeen(user.getLastSeen())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static UserResponseBuilder builder() {
        return new UserResponseBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public boolean isHasAvatar() {
        return hasAvatar;
    }

    public void setHasAvatar(boolean hasAvatar) {
        this.hasAvatar = hasAvatar;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class UserResponseBuilder {
        private String id;
        private String username;
        private String email;
        private String profileImage;
        private boolean hasAvatar;
        private Instant avatarUpdatedAt;
        private String bio;
        private boolean online;
        private Instant lastSeen;
        private Instant createdAt;
        private Instant updatedAt;

        UserResponseBuilder() {
        }

        public UserResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public UserResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserResponseBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserResponseBuilder profileImage(String profileImage) {
            this.profileImage = profileImage;
            return this;
        }

        public UserResponseBuilder hasAvatar(boolean hasAvatar) {
            this.hasAvatar = hasAvatar;
            return this;
        }

        public UserResponseBuilder avatarUpdatedAt(Instant avatarUpdatedAt) {
            this.avatarUpdatedAt = avatarUpdatedAt;
            return this;
        }

        public UserResponseBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public UserResponseBuilder online(boolean online) {
            this.online = online;
            return this;
        }

        public UserResponseBuilder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public UserResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserResponseBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserResponse build() {
            return new UserResponse(id, username, email, profileImage,
                    hasAvatar, avatarUpdatedAt, bio, online, lastSeen, createdAt, updatedAt);
        }
    }
}

