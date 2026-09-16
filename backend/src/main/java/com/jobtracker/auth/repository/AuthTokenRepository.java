package com.jobtracker.auth.repository;

import com.jobtracker.auth.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE AuthToken t SET t.consumedAt = :now WHERE t.id = :id AND t.consumedAt IS NULL AND t.expiresAt > :now")
    int consumeToken(@Param("id") UUID id, @Param("now") Instant now);
}
