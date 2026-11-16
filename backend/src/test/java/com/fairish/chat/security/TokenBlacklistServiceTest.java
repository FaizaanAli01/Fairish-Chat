package com.fairish.chat.security;

import com.fairish.chat.exception.TokenBlacklistException;
import com.fairish.chat.model.BlacklistedToken;
import com.fairish.chat.repository.BlacklistedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenBlacklistServiceTest {

    private BlacklistedTokenRepository repository;
    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        repository = mock(BlacklistedTokenRepository.class);
        when(repository.findByExpiresAtAfter(any(Instant.class))).thenReturn(List.of());
        blacklistService = new TokenBlacklistService(repository);
        blacklistService.refreshFromStore();
    }

    @Test
    @DisplayName("Should detect blacklisted tokens until expiration")
    void shouldTrackBlacklistedTokens() {
        String token = "sample-active-token";
        Instant expiresAt = Instant.now().plusSeconds(300);

        assertFalse(blacklistService.isBlacklisted(token));

        blacklistService.blacklistToken(token, expiresAt);
        assertTrue(blacklistService.isBlacklisted(token));
        verify(repository).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("Should persist only a digest of the raw token")
    void shouldPersistOnlyTokenDigest() {
        String token = "sample-active-token";
        blacklistService.blacklistToken(token, Instant.now().plusSeconds(300));

        ArgumentCaptor<BlacklistedToken> captor = ArgumentCaptor.forClass(BlacklistedToken.class);
        verify(repository).save(captor.capture());

        BlacklistedToken saved = captor.getValue();
        assertNotEquals(token, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    @DisplayName("Should reload persisted revocations after a restart")
    void shouldReloadPersistedRevocations() {
        String token = "survives-restart";
        blacklistService.blacklistToken(token, Instant.now().plusSeconds(300));

        ArgumentCaptor<BlacklistedToken> captor = ArgumentCaptor.forClass(BlacklistedToken.class);
        verify(repository).save(captor.capture());

        BlacklistedTokenRepository freshRepository = mock(BlacklistedTokenRepository.class);
        when(freshRepository.findByExpiresAtAfter(any(Instant.class)))
                .thenReturn(List.of(captor.getValue()));

        TokenBlacklistService restarted = new TokenBlacklistService(freshRepository);
        restarted.refreshFromStore();

        assertTrue(restarted.isBlacklisted(token));
    }

    @Test
    @DisplayName("Should not blacklist tokens already in the past")
    void shouldIgnoreAlreadyExpiredTokens() {
        String token = "expired-token";
        Instant past = Instant.now().minusSeconds(10);

        blacklistService.blacklistToken(token, past);
        assertFalse(blacklistService.isBlacklisted(token));
        verify(repository, never()).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("Should safely handle null and blank tokens")
    void shouldHandleNullOrBlank() {
        assertFalse(blacklistService.isBlacklisted(null));
        assertFalse(blacklistService.isBlacklisted("   "));

        assertDoesNotThrow(() -> blacklistService.blacklistToken(null, Instant.now().plusSeconds(60)));
        assertDoesNotThrow(() -> blacklistService.blacklistToken("   ", Instant.now().plusSeconds(60)));
        verify(repository, never()).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("Should fail closed when the revocation list has never been loaded")
    void shouldFailClosedBeforeFirstLoad() {
        TokenBlacklistService unloaded = new TokenBlacklistService(repository);

        assertThrows(TokenBlacklistException.class, () -> unloaded.isBlacklisted("some-token"));
    }

    @Test
    @DisplayName("Should surface persistence failures but still revoke locally")
    void shouldSurfacePersistenceFailure() {
        String token = "persistence-failure";
        when(repository.save(any(BlacklistedToken.class)))
                .thenThrow(new RuntimeException("database down"));

        assertThrows(TokenBlacklistException.class,
                () -> blacklistService.blacklistToken(token, Instant.now().plusSeconds(300)));
        assertTrue(blacklistService.isBlacklisted(token));
    }

    @Test
    @DisplayName("Should keep the last known-good mirror when a refresh fails")
    void shouldKeepMirrorWhenRefreshFails() {
        String token = "mirror-survives";
        blacklistService.blacklistToken(token, Instant.now().plusSeconds(300));

        when(repository.findByExpiresAtAfter(any(Instant.class)))
                .thenThrow(new RuntimeException("database down"));
        blacklistService.refreshFromStore();

        assertTrue(blacklistService.isBlacklisted(token));
    }

    @Test
    @DisplayName("Should drop expired entries from the mirror")
    void shouldDropExpiredEntries() {
        String token = "short-lived";
        blacklistService.blacklistToken(token, Instant.now().plusMillis(200));

        try {
            Thread.sleep(300);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        assertFalse(blacklistService.isBlacklisted(token));
    }
}
