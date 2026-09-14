package com.jobtracker.auth.controller;

import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
class PasswordResetRevokesSessionsIT {

    private MockMvc mockMvc;

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private AuthService authService;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
        refreshTokenRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRevokeAllSessionsOnPasswordReset() throws Exception {
        User user = new User();
        user.setEmail("reset@example.com");
        user.setEmailNormalized("reset@example.com");
        user.setPasswordHash(passwordEncoder.encode("old_pw"));
        user.setName("Reset User");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        // Login to create a session
        com.jobtracker.auth.dto.LoginRequest loginRequest = new com.jobtracker.auth.dto.LoginRequest("reset@example.com", "old_pw");
        authService.login(loginRequest, "127.0.0.1", "test-agent");
        
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        
        // Request reset
        mockMvc.perform(post("/api/v1/auth/password-reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isAccepted());
                
        // In a real flow, we'd extract the token from the DB and use it here.
        // For this test, we can just call the service method directly to simulate the confirm step
        var authToken = authTokenRepository.findAll().stream().filter(t -> "PASSWORD_RESET".equals(t.getTokenType())).findFirst().orElseThrow();
        
        // Wait, the test uses confirmJson containing the raw token. 
        // But the db only stores the hash. We can't reverse the hash. 
        // So for this test, we can just insert a token where we know the raw value.
        // Actually, since this is an IT, let's just make the /password-reset/request endpoint create the token,
        // but we can't extract it. It's sent via email. 
        // So we just mock the TokenEncryptionService or something.
        // Or we manually insert the AuthToken with a known hash.
        String rawToken = "known-token";
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String actualHash = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);

        com.jobtracker.auth.entity.AuthToken resetToken = new com.jobtracker.auth.entity.AuthToken();
        resetToken.setUserId(user.getId());
        resetToken.setTokenHash(actualHash);
        resetToken.setTokenType("PASSWORD_RESET");
        resetToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        authTokenRepository.save(resetToken);

        String confirmJson = "{\"token\":\"" + rawToken + "\",\"newPassword\":\"New_pw123\"}";
        
        mockMvc.perform(post("/api/v1/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson))
                .andExpect(status().isOk());
                
        // Verify all refresh tokens are revoked
        assertThat(refreshTokenRepository.findAll()).allMatch(token -> token.getRevokedAt() != null);
        
        // Verify we can login with new password
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\",\"password\":\"New_pw123\"}"))
                .andExpect(status().isOk());
    }
}
