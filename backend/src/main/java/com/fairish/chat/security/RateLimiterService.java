package com.fairish.chat.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding window rate limiter service.
 * Protects critical endpoints like /api/auth/login and /api/auth/register against brute-force
 * and denial-of-service attempts (SEC-03 fix).
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.rate-limit.login-per-minute:10}")
    private int loginLimit = 10;

    @Value("${app.rate-limit.register-per-minute:5}")
    private int registerLimit = 5;

    /**
     * Per-account limit, applied in addition to the per-IP limit. This prevents
     * an attacker from spreading guesses for one account across many source
     * addresses. The identifier is hashed before it is used as a key, so an
     * email address or username never appears in memory keys or logs.
     */
    @Value("${app.rate-limit.login-per-account-per-minute:5}")
    private int loginAccountLimit = 5;

    /**
     * Entries are only retained for the longest supported window. The
     * scheduled cleanup uses this value so a longer custom window cannot be
     * evicted while it is still active.
     */
    private static final long MAX_RETENTION_MILLIS = Duration.ofHours(1).toMillis();

    private final ConcurrentHashMap<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (loginLimit <= 0) {
            throw new IllegalStateException("app.rate-limit.login-per-minute must be greater than zero");
        }
        if (registerLimit <= 0) {
            throw new IllegalStateException("app.rate-limit.register-per-minute must be greater than zero");
        }
        if (loginAccountLimit <= 0) {
            throw new IllegalStateException(
                    "app.rate-limit.login-per-account-per-minute must be greater than zero");
        }
    }

    public boolean allowLogin(String clientIp) {
        if (!enabled) {
            return true;
        }
        return isAllowed("login:" + clientIp, loginLimit, Duration.ofMinutes(1));
    }

    public boolean allowRegister(String clientIp) {
        if (!enabled) {
            return true;
        }
        return isAllowed("register:" + clientIp, registerLimit, Duration.ofMinutes(1));
    }

    /**
     * Applies a per-account login limit. The identifier is normalized and
     * hashed; only the digest is retained and logged.
     */
    public boolean allowLoginForAccount(String identifier) {
        if (!enabled || !StringUtils.hasText(identifier)) {
            return true;
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        String key = "login-account:" + sha256Hex(normalized);
        return isAllowed(key, loginAccountLimit, Duration.ofMinutes(1));
    }

    public boolean isAllowed(String key, int maxRequests, Duration window) {
        if (!enabled || maxRequests <= 0) {
            return true;
        }

        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.toMillis();

        Deque<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                // Never log the key: it can contain a client IP or a hashed
                // account identifier.
                log.warn("Rate limit exceeded for a protected operation");
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupStaleEntries() {
        long cutoff = Instant.now().toEpochMilli() - MAX_RETENTION_MILLIS;
        requestLogs.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }

    public void clear() {
        requestLogs.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", ex);
        }
    }
}
