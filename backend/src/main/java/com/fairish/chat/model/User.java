package com.fairish.chat.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;
    private String profileImage;

    /**
     * Private storage key for the current avatar. Never exposed through any API
     * response. Null when the user has not uploaded an avatar.
     */
    private String avatarKey;

    private String avatarContentType;
    private Instant avatarUpdatedAt;

    private String bio;
    private boolean online = false;
    private Instant lastSeen;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public User() {
    }

    public User(String id, String username, String email, String passwordHash, String profileImage,
                String avatarKey, String avatarContentType, Instant avatarUpdatedAt,
                String bio, boolean online, Instant lastSeen, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.profileImage = profileImage;
        this.avatarKey = avatarKey;
        this.avatarContentType = avatarContentType;
        this.avatarUpdatedAt = avatarUpdatedAt;
        this.bio = bio;
        this.online = online;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserBuilder builder() {
        return new UserBuilder();
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public boolean hasAvatar() {
        return avatarKey != null && !avatarKey.isBlank();
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

    public static class UserBuilder {
        private String id;
        private String username;
        private String email;
        private String passwordHash;
        private String profileImage;
        private String avatarKey;
        private String avatarContentType;
        private Instant avatarUpdatedAt;
        private String bio;
        private boolean online = false;
        private Instant lastSeen;
        private Instant createdAt;
        private Instant updatedAt;

        UserBuilder() {
        }

        public UserBuilder id(String id) {
            this.id = id;
            return this;
        }

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public UserBuilder profileImage(String profileImage) {
            this.profileImage = profileImage;
            return this;
        }

        public UserBuilder avatarKey(String avatarKey) {
            this.avatarKey = avatarKey;
            return this;
        }

        public UserBuilder avatarContentType(String avatarContentType) {
            this.avatarContentType = avatarContentType;
            return this;
        }

        public UserBuilder avatarUpdatedAt(Instant avatarUpdatedAt) {
            this.avatarUpdatedAt = avatarUpdatedAt;
            return this;
        }

        public UserBuilder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public UserBuilder online(boolean online) {
            this.online = online;
            return this;
        }

        public UserBuilder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public UserBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public User build() {
            return new User(id, username, email, passwordHash, profileImage,
                    avatarKey, avatarContentType, avatarUpdatedAt,
                    bio, online, lastSeen, createdAt, updatedAt);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, email);
    }
}

