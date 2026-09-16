package com.jobtracker.auth.service;

import com.jobtracker.auth.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void shouldGenerateAndDecodeToken() throws Exception {
        JwtProperties properties = new JwtProperties(
                null,
                null,
                null,
                "dummy-active-id",
                "dummy-previous-id",
                900L
        );
        // Test will generate its own RSA key since privateKey/publicKey are null.
        JwtService jwtService = new JwtService(properties);

        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@example.com", "USER");

        Jwt jwt = jwtService.decodeToken(token);
        
        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("test@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getIssuedAt()).isNotNull();
    }

    @Test
    void shouldFailDecodeOnInvalidToken() throws Exception {
        JwtProperties properties = new JwtProperties(null, null, null, "k1", null, 900L);
        JwtService jwtService = new JwtService(properties);

        assertThrows(JwtException.class, () -> jwtService.decodeToken("invalid-token"));
    }

    @Test
    void shouldInitializeWithProvidedPemKeys() throws Exception {
        java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        java.security.KeyPair keyPair1 = keyPairGenerator.generateKeyPair();
        java.security.KeyPair keyPair2 = keyPairGenerator.generateKeyPair();

        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getEncoder().encodeToString(keyPair1.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(keyPair1.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        
        String prevPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(keyPair2.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";

        JwtProperties properties = new JwtProperties(
                privateKeyPem,
                publicKeyPem,
                prevPublicKeyPem,
                "key-1",
                "key-0",
                900L
        );
        
        JwtService jwtService = new JwtService(properties);
        
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test2@example.com", "ADMIN");

        Jwt jwt = jwtService.decodeToken(token);
        
        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("email")).isEqualTo("test2@example.com");
    }
}
