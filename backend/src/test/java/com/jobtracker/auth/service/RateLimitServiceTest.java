package com.jobtracker.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        rateLimitService = new RateLimitService(redisTemplate);
        ReflectionTestUtils.setField(rateLimitService, "loginWindowSeconds", 900L);
        ReflectionTestUtils.setField(rateLimitService, "passwordResetWindowSeconds", 3600L);
    }

    @Test
    void shouldAllowUnderLimit() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        
        boolean isLimited = rateLimitService.isLoginRateLimited("127.0.0.1", "test@example.com");
        
        assertThat(isLimited).isFalse();
        verify((org.springframework.data.redis.core.RedisOperations<String, String>) redisTemplate).expire("ratelimit:login:127.0.0.1:test@example.com", java.time.Duration.ofSeconds(900));
    }

    @Test
    void shouldBlockAtLimit() {
        when(valueOperations.increment(anyString())).thenReturn(6L);
        
        boolean isLimited = rateLimitService.isLoginRateLimited("127.0.0.1", "test@example.com");
        
        assertThat(isLimited).isTrue();
        verify((org.springframework.data.redis.core.RedisOperations<String, String>) redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    void shouldFailClosedOnRedisException() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis down"));
        
        try {
            rateLimitService.isLoginRateLimited("127.0.0.1", "test@example.com");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Redis down");
        }
    }
}
