package com.fairish.chat.security;

import com.fairish.chat.exception.TokenBlacklistException;
import com.fairish.chat.model.BlacklistedToken;
import com.fairish.chat.repository.BlacklistedTokenRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revocation list for logged-out JWTs.
 *
 * <p>Revocations are persisted in MongoDB so they survive an application
 * restart and are shared between instances. A short-lived in-memory mirror
 * serves the hot path: {@link #isBlacklisted(String)} never touches the
 * database once the mirror has been loaded, which keeps a database read off
 * every authenticated request.</p>
 *
 * <p>The mirror is refreshed on startup and on a fixed schedule. A logout
 * handled by this instance takes effect immediately; a logout handled by a
 * different instance becomes visible after at most one refresh interval. Only
 * SHA-256 digests are stored, never the raw token.</p>
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final long DEFAULT_RETENTION_MILLIS = 86_400_000L;

    private final BlacklistedTokenRepository repository;

    /** token hash -> epoch millis at which the JWT expires. */
    private final ConcurrentHashMap<String, Long> localMirror = new ConcurrentHashMap<>();

    /**
     * False until the mirror has been loaded at least once. While false the
     * service fails closed instead of assuming that a token is not revoked.
     */
    private volatile boolean mirrorLoaded = false;

    public TokenBlacklistService(BlacklistedTokenRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void loadOnStartup() {
        refreshFromStore();
    }

    /**
     * Reloads every unexpired revocation from MongoDB. Failures keep the last
     * known-good mirror rather than clearing it.
     */
    @Scheduled(fixedDelayString = "${app.security.token-blacklist.refresh-ms:5000}")
    public synchronized void refreshFromStore() {
        try {
            Instant now = Instant.now();
            List<BlacklistedToken> revoked = repository.findByExpiresAtAfter(now);
            for (BlacklistedToken entry : revoked) {
                if (entry.getTokenHash() != null && entry.getExpiresAt() != null) {
                    localMirror.put(entry.getTokenHash(), entry.getExpiresAt().toEpochMilli());
                }
            }
            long nowMillis = now.toEpochMilli();
            localMirror.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
            mirrorLoaded = true;
        } catch (RuntimeException ex) {
            log.error("Could not refresh the token revocation list: {}", ex.getClass().getSimpleName());
        }
    }

    /**
     * Revokes a token until its natural expiry.
     *
     * @throws TokenBlacklistException when the revocation cannot be persisted
     */
    public void blacklistToken(String token, Instant expiration) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        Instant expiresAt = expiration != null
                ? expiration
                : Instant.now().plusMillis(DEFAULT_RETENTION_MILLIS);
        if (!expiresAt.isAfter(Instant.now())) {
            // Already expired: the JWT is unusable regardless.
            return;
        }

        String tokenHash = hash(token);
        // Apply locally first so a persistence failure still invalidates the
        // token on this instance, then surface the failure to the caller.
        localMirror.put(tokenHash, expiresAt.toEpochMilli());
        try {
            repository.save(new BlacklistedToken(tokenHash, expiresAt));
        } catch (RuntimeException ex) {
            throw new TokenBlacklistException("Failed to persist token revocation", ex);
        }
    }

    /**
     * @throws TokenBlacklistException when the mirror has never been loaded and
     *         the caller therefore cannot trust a negative answer
     */
    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        String tokenHash = hash(token);
        Long expiresAt = localMirror.get(tokenHash);
        if (expiresAt != null) {
            if (System.currentTimeMillis() >= expiresAt) {
                localMirror.remove(tokenHash, expiresAt);
                return false;
            }
            return true;
        }

        if (!mirrorLoaded) {
            // Fail closed: without a loaded mirror an absent entry proves nothing.
            throw new TokenBlacklistException(
                    "Token revocation list is unavailable",
                    new IllegalStateException("Revocation list has not been loaded"));
        }
        return false;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        localMirror.entrySet().removeIf(entry -> entry.getValue() <= now);
        try {
            repository.deleteByExpiresAtBefore(Instant.ofEpochMilli(now));
        } catch (RuntimeException ex) {
            log.error("Could not purge expired token revocations: {}", ex.getClass().getSimpleName());
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", ex);
        }
    }
}
