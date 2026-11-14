package com.fairish.chat.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for message encryption at rest.
 *
 * <p>Secrets are supplied only through the environment (or an external secret
 * manager) and never have usable defaults. {@link #key} is the active AES-256
 * key encoded as standard Base64; it must decode to exactly 32 bytes.</p>
 *
 * <p>{@link #previousKeys} is an optional comma-separated list of
 * {@code keyId:base64Key} entries. Previous keys are used only for decryption,
 * which makes key rotation possible without re-encrypting existing messages.</p>
 */
@ConfigurationProperties(prefix = "app.encryption")
public class MessageEncryptionProperties {

    private boolean enabled = true;
    private String activeKeyId = "v1";
    private String key;
    private String previousKeys;
    private final Migration migration = new Migration();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPreviousKeys() {
        return previousKeys;
    }

    public void setPreviousKeys(String previousKeys) {
        this.previousKeys = previousKeys;
    }

    public Migration getMigration() {
        return migration;
    }

    public static class Migration {

        private boolean enabled = false;
        private String mode = "dry-run";
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
