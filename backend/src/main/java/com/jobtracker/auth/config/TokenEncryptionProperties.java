package com.jobtracker.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.token-encryption")
public record TokenEncryptionProperties(String key) {
}
