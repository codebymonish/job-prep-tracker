package com.jobtracker.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.dto.LoginRequest;
import com.jobtracker.auth.dto.RegisterRequest;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
public class RefreshTokenRotationIT {

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setup() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build();
        refreshTokenRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testConcurrentRefreshRotation() throws Exception {
        // Setup user
        RegisterRequest registerReq = new RegisterRequest("rotate@example.com", "Password123!", "Rotate Tester");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Verify user
        User user = userRepository.findByEmailNormalized("rotate@example.com").orElseThrow();
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // Login to get original token (R1)
        LoginRequest loginReq = new LoginRequest("rotate@example.com", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(refreshTokenCookie);

        // Use CyclicBarrier to ensure both threads fire refresh simultaneously
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<MvcResult> refresh1 = executor.submit(() -> {
            barrier.await();
            return mockMvc.perform(post("/api/v1/auth/refresh")
                    .cookie(refreshTokenCookie))
                    .andReturn();
        });

        Future<MvcResult> refresh2 = executor.submit(() -> {
            barrier.await();
            return mockMvc.perform(post("/api/v1/auth/refresh")
                    .cookie(refreshTokenCookie))
                    .andReturn();
        });

        MvcResult result1 = refresh1.get();
        MvcResult result2 = refresh2.get();

        // 1. Assert both responses are 200 OK
        assertEquals(200, result1.getResponse().getStatus());
        assertEquals(200, result2.getResponse().getStatus());

        // 2. Assert both receive byte-identical R2 cookies
        String r2_1 = result1.getResponse().getCookie("refresh_token").getValue();
        String r2_2 = result2.getResponse().getCookie("refresh_token").getValue();
        assertEquals(r2_1, r2_2, "Both concurrent callers must receive the exact same R2 refresh token");

        // 3. Assert both receive byte-identical J2 JSON payloads
        String body1 = result1.getResponse().getContentAsString();
        String body2 = result2.getResponse().getContentAsString();
        assertEquals(body1, body2, "Both concurrent callers must receive the exact same J2 response payload");

        // 4. Assert exactly 2 refresh_tokens rows exist for the family (the old revoked one, and the new R2)
        assertEquals(2, refreshTokenRepository.count(), "There should be exactly 2 rows in the token family");

        executor.shutdown();
    }
}
