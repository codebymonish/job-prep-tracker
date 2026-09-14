package com.jobtracker.auth.controller;

import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    private MockMvc mockMvc;

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void shouldBlockLoginAfterTooManyFailedAttempts() throws Exception {
        User user = new User();
        user.setEmail("ratelimit@example.com");
        user.setEmailNormalized("RATELIMIT@EXAMPLE.COM");
        user.setPasswordHash(passwordEncoder.encode("correct_pw"));
        user.setName("Rate Limit User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        String loginJson = "{\"email\":\"ratelimit@example.com\",\"password\":\"wrong_pw\"}";

        // 5 failed attempts allowed
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should return 429 Too Many Requests (or generic Bad Request depending on implementation, here we expect 400 Bad Request or 401 based on our exception handler)
        // Wait, ProblemDetailConfig maps IllegalArgumentException to 401 if it contains "Invalid", but this is "Too many login attempts", which maps to 400 Bad Request. Let's see what the actual HTTP status is.
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isBadRequest());
    }
}
