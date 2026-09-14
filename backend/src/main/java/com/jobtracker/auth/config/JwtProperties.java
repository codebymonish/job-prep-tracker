package com.jobtracker.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        String previousPublicKey,
        String keyId,
        String previousKeyId,
        long expirySeconds) {
}
