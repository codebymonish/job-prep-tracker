package com.jobtracker.auth.controller;

import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.auth.entity.AuthToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
class EmailVerificationIT {

    private MockMvc mockMvc;

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldVerifyEmailAndActivateUser() throws Exception {
        User user = new User();
        user.setEmail("unverified@example.com");
        user.setEmailNormalized("UNVERIFIED@EXAMPLE.COM");
        user.setPasswordHash(passwordEncoder.encode("pw123"));
        user.setName("Unverified User");
        user.setStatus("UNVERIFIED");
        user = userRepository.save(user);

        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest("verify-token-123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String actualHash = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        
        token.setTokenHash(actualHash);
        token.setTokenType("EMAIL_VERIFY");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        authTokenRepository.save(token);

        // The real controller would hash the token "verify-token-123" and compare it with the DB.
        // Assuming the controller hashes "verify-token-123" to "verify-token-123-hash" internally or whatever logic it uses.
        // If it's a mock or real password encoder, we should probably just use whatever the controller uses.
        // Since we didn't mock the token hash, wait, the controller hashes the verify token with SHA-256.
        // For testing, we might need a real SHA-256 hash if the controller checks it.
        // Actually, if we just want to compile, let's leave the logic as is and just fix the method calls.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/verify?token=verify-token-123"))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getStatus()).isEqualTo("ACTIVE");

        // Token should be consumed
        assertThat(authTokenRepository.findByTokenHash(actualHash).get().getConsumedAt()).isNotNull();
    }
}
