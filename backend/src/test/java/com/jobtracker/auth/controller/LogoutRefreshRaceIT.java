package com.jobtracker.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.dto.LoginRequest;
import com.jobtracker.auth.dto.LoginResponse;
import com.jobtracker.auth.dto.RegisterRequest;
import com.jobtracker.auth.entity.AuthToken;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
public class LogoutRefreshRaceIT {

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @MockitoSpyBean
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build();
        refreshTokenRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testLogoutAndRefreshConcurrency() throws Exception {
        // Setup user
        RegisterRequest registerReq = new RegisterRequest("race@example.com", "Password123!", "Race Tester");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Verify user manually
        User user = userRepository.findByEmailNormalized("race@example.com").orElseThrow();
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // Login
        LoginRequest loginReq = new LoginRequest("race@example.com", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refresh_token");
        assertTrue(refreshTokenCookie != null && !refreshTokenCookie.getValue().isBlank());

        // Inject a 500ms sleep into revokeFamily to GUARANTEE logout holds the DB lock
        // while refresh attempts to acquire it via SELECT FOR UPDATE in findByTokenHash.
        // We use jdbcTemplate to perform the actual update since we cannot callRealMethod() 
        // on a Spring Data Proxy interface.
        doAnswer(invocation -> {
            Thread.sleep(500);
            UUID familyId = invocation.getArgument(0);
            Timestamp now = Timestamp.from((Instant) invocation.getArgument(1));
            return jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = ? WHERE family_id = ?", now, familyId);
        }).when(refreshTokenRepository).revokeFamily(any(), any());

        // Concurrency test using CyclicBarrier(2)
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Integer> logoutStatus = executor.submit(() -> {
            barrier.await();
            return mockMvc.perform(post("/api/v1/auth/logout")
                    .cookie(refreshTokenCookie))
                    .andReturn().getResponse().getStatus();
        });

        Future<Integer> refreshStatus = executor.submit(() -> {
            barrier.await();
            // Start very slightly after to ensure logout initiates the transaction first,
            // but refresh will genuinely block on the DB lock because of the 500ms sleep!
            Thread.sleep(50); 
            return mockMvc.perform(post("/api/v1/auth/refresh")
                    .cookie(refreshTokenCookie))
                    .andReturn().getResponse().getStatus();
        });

        int lStatus = logoutStatus.get();
        int rStatus = refreshStatus.get();

        // 1. Assert exactly the expected HTTP statuses for "logout wins" scenario
        assertEquals(204, lStatus, "Logout must return 204 No Content");
        assertEquals(401, rStatus, "Refresh must return 401 Unauthorized because the token was revoked by the concurrent logout");

        // 2. Assert all tokens in the family are actually revoked
        long activeTokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getRevokedAt() == null)
                .count();

        assertEquals(0, activeTokens, "All tokens in the family should be revoked after logout");

        executor.shutdown();
    }
}
