package com.fairish.chat.dto;

import com.fairish.chat.model.User;

import java.time.Instant;

/**
 * Public projection of a user.
 *
 * <p>This DTO is used whenever a user is returned to somebody other than that
 * user: user search results and the other participant in a conversation. It
 * deliberately omits the email address so another user's email is never
 * exposed through the API.</p>
 *
 * <p>Use {@link UserResponse} for the authenticated user's own profile
 * ({@code /api/users/me}) and authentication responses, where the email is
 * legitimately needed by the account owner.</p>
 */
public class PublicUserResponse {

    private String id;
    private String username;
    private String profileImage;
    private String bio;
    private boolean online;
    private Instant lastSeen;
    private Instant createdAt;
    private Instant updatedAt;

    public PublicUserResponse() {
    }

    public PublicUserResponse(String id, String username, String profileImage, String bio,
                              boolean online, Instant lastSeen, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.profileImage = profileImage;
        this.bio = bio;
        this.online = online;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PublicUserResponse fromUser(User user) {
        if (user == null) {
            return null;
        }
        return PublicUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .profileImage(user.getProfileImage())
                .bio(user.getBio())
                .online(user.isOnline())
                .lastSeen(user.getLastSeen())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static PublicUserResponseBuilder builder() {
        return new PublicUserResponseBuilder();
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

    public static class PublicUserResponseBuilder {
        private String id;
        private String username;
        private String profileImage;
        private String bio;
        private boolean online;
        private Instant lastSeen;
        private Instant createdAt;
        private Instant updatedAt;

        PublicUserResponseBuilder() {
        }

        public PublicUserResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public PublicUserResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public PublicUserResponseBuilder profileImage(String profileImage) {
            this.profileImage = profileImage;
            return this;
        }

        public PublicUserResponseBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public PublicUserResponseBuilder online(boolean online) {
            this.online = online;
            return this;
        }

        public PublicUserResponseBuilder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public PublicUserResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public PublicUserResponseBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PublicUserResponse build() {
            return new PublicUserResponse(id, username, profileImage, bio, online, lastSeen, createdAt, updatedAt);
        }
    }
}
