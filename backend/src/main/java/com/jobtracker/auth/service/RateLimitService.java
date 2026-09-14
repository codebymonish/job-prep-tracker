package com.jobtracker.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    
    @Value("${app.auth.rate-limit.login-window-seconds:900}")
    private long loginWindowSeconds;
    
    @Value("${app.auth.rate-limit.password-reset-window-seconds:3600}")
    private long passwordResetWindowSeconds;
    
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_PASSWORD_RESET_ATTEMPTS = 3;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isLoginRateLimited(String ip, String emailNormalized) {
        String key = "ratelimit:login:" + ip + ":" + emailNormalized;
        return isRateLimited(key, MAX_LOGIN_ATTEMPTS, loginWindowSeconds);
    }

    public boolean isPasswordResetRateLimited(String emailNormalized) {
        String key = "ratelimit:pwreset:" + emailNormalized;
        return isRateLimited(key, MAX_PASSWORD_RESET_ATTEMPTS, passwordResetWindowSeconds);
    }

    private boolean isRateLimited(String key, int maxAttempts, long windowSeconds) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return attempts != null && attempts > maxAttempts;
    }
    
    public long getLoginWindowSeconds() {
        return loginWindowSeconds;
    }
}
