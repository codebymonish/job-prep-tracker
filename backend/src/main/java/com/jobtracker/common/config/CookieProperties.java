package com.jobtracker.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.Cookie.SameSite;

@ConfigurationProperties("app.cookie")
public record CookieProperties(
        boolean secure,
        SameSite sameSite,
        String path,
        long maxAgeSeconds) {
}
