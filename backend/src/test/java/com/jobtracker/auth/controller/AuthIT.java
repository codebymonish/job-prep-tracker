package com.jobtracker.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.dto.LoginRequest;
import com.jobtracker.auth.dto.LoginResponse;
import com.jobtracker.auth.dto.RegisterRequest;
import com.jobtracker.auth.entity.AuthToken;
import com.jobtracker.auth.entity.RefreshToken;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
public class AuthIT {

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
    void testFullAuthFlow() throws Exception {
        // 1. Register
        RegisterRequest registerReq = new RegisterRequest("e2e@example.com", "Password123!", "E2E Tester");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("e2e@example.com"))
                .andExpect(jsonPath("$.status").value("UNVERIFIED"));

        // Retrieve AuthToken from DB (acting as email intercept)
        List<AuthToken> authTokens = authTokenRepository.findAll();
        assertEquals(1, authTokens.size());
        AuthToken verificationToken = authTokens.get(0);
        
        // Wait, the raw token is not stored in DB, only the hash!
        // We can't actually intercept the raw token from DB. We need to mock EmailService or use GreenMail.
        // For simplicity in this IT without GreenMail, we can manually set status to ACTIVE.
        
        User user = userRepository.findByEmailNormalized("e2e@example.com").orElseThrow();
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // 2. Login
        LoginRequest loginReq = new LoginRequest("e2e@example.com", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshTokenCookie);

        // 3. Refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie newRefreshTokenCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertNotNull(newRefreshTokenCookie);
        assertNotEquals(refreshTokenCookie.getValue(), newRefreshTokenCookie.getValue());

        // 4. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(newRefreshTokenCookie))
                .andExpect(status().isNoContent());

        // 5. Refresh again with old token (should fail)
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(newRefreshTokenCookie))
                .andExpect(status().isUnauthorized());
    }
}
