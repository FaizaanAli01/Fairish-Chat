package com.fairish.chat.repository;

import com.fairish.chat.model.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface BlacklistedTokenRepository extends MongoRepository<BlacklistedToken, String> {

    List<BlacklistedToken> findByExpiresAtAfter(Instant cutoff);

    long deleteByExpiresAtBefore(Instant cutoff);
}
