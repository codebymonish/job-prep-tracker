package com.jobtracker.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.dto.*;
import com.jobtracker.auth.entity.AuthToken;
import com.jobtracker.auth.entity.RefreshToken;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.data.redis.url=")
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @Test
    void register_success_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest("test@example.com", "Password123!", "Test User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.status", is("UNVERIFIED")))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        User user = new User();
        user.setEmail("test@example.com");
        user.setEmailNormalized("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        RegisterRequest req = new RegisterRequest("Test@Example.com", "Password123!", "Another User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", endsWith("/email-exists")))
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void verify_validToken_returns200() throws Exception {
        User user = new User();
        user.setEmail("verify@example.com");
        user.setEmailNormalized("verify@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("UNVERIFIED");
        userRepository.save(user);

        // We can't easily mock the 32-byte secure random token here without AuthService, 
        // but we can register via endpoint to get the token, except EmailService is async.
        // Actually, we can just create a manual token hash if we know how it's done.
        // Or we can just use the login endpoint to test login.
        // But the prompt wants us to test EVERY endpoint.
        
        String rawToken = "test_raw_token";
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tokenHash = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setTokenType("EMAIL_VERIFY");
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        authTokenRepository.save(token);

        mockMvc.perform(get("/api/v1/auth/verify")
                        .param("token", rawToken))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assert updatedUser.getStatus().equals("ACTIVE");
    }

    @Test
    void login_success_returnsCookieAndToken() throws Exception {
        User user = new User();
        user.setEmail("login@example.com");
        user.setEmailNormalized("login@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        LoginRequest req = new LoginRequest("login@example.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().maxAge("refreshToken", 604800))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.expiresIn", is(900)));
    }

    @Test
    void login_invalidPassword_returns401() throws Exception {
        User user = new User();
        user.setEmail("invalid@example.com");
        user.setEmailNormalized("invalid@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        LoginRequest req = new LoginRequest("invalid@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", endsWith("/unauthorized")));
    }

    @Test
    void refresh_validCookie_returnsNewTokens() throws Exception {
        // Step 1: Login to get a valid refresh cookie
        User user = new User();
        user.setEmail("refresh@example.com");
        user.setEmailNormalized("refresh@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        LoginRequest req = new LoginRequest("refresh@example.com", "Password123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        // Step 2: Call refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().value("refreshToken", not(refreshCookie.getValue())))
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_validCookie_clearsCookieAndRevokes() throws Exception {
        User user = new User();
        user.setEmail("logout@example.com");
        user.setEmailNormalized("logout@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        LoginRequest req = new LoginRequest("logout@example.com", "Password123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().value("refreshToken", ""));
    }
    
    @Test
    void passwordResetRequest_validEmail_returns202() throws Exception {
        PasswordResetRequestDto req = new PasswordResetRequestDto("doesnotexist@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }

    @Test
    void passwordReset_validToken_returns200() throws Exception {
        User user = new User();
        user.setEmail("pwreset@example.com");
        user.setEmailNormalized("pwreset@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        String rawToken = "pw_reset_token";
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tokenHash = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setTokenType("PASSWORD_RESET");
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        authTokenRepository.save(token);

        PasswordResetDto req = new PasswordResetDto(rawToken, "NewPassword123!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assert passwordEncoder.matches("NewPassword123!", updatedUser.getPasswordHash());
    }
}
