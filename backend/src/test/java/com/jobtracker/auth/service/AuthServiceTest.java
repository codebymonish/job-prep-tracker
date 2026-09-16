package com.jobtracker.auth.service;

import com.jobtracker.common.config.CookieProperties;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthTokenRepository authTokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RateLimitService rateLimitService;
    private EmailService emailService;
    private TokenEncryptionService tokenEncryptionService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authTokenRepository = mock(AuthTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        rateLimitService = mock(RateLimitService.class);
        emailService = mock(EmailService.class);
        tokenEncryptionService = mock(TokenEncryptionService.class);

        com.jobtracker.common.config.CookieProperties cookieProperties = new com.jobtracker.common.config.CookieProperties(true, org.springframework.boot.web.server.Cookie.SameSite.STRICT, "/api/v1/auth", 3600L);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                authTokenRepository,
                passwordEncoder,
                jwtService,
                tokenEncryptionService,
                rateLimitService,
                emailService,
                cookieProperties,
                objectMapper
        );
    }

    @Test
    void login_success() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed_pw");
        user.setStatus("ACTIVE");
        user.setRole("USER");

        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw_pw", "hashed_pw")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), user.getEmail(), user.getRole())).thenReturn("jwt-token");
        when(tokenEncryptionService.encrypt(anyString())).thenReturn("encrypted-rt");

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "raw_pw");
        com.jobtracker.auth.service.AuthService.TokenPair result = authService.login(loginReq, "127.0.0.1", "test-agent");

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.rawRefreshToken()).isNotEmpty();
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    void login_invalidCredentials() {
        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.empty());

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "wrong_pw");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginReq, "127.0.0.1", "test-agent"));
        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void login_rateLimited() {
        when(rateLimitService.isLoginRateLimited("127.0.0.1", "test@example.com")).thenReturn(true);

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "pw");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginReq, "127.0.0.1", "test-agent"));
        assertThat(ex.getMessage()).isEqualTo("Too many login attempts");
    }

    @Test
    void login_accountNotActive() {
        User user = new User();
        user.setStatus("UNVERIFIED");
        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.of(user));

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "pw");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginReq, "127.0.0.1", "test-agent"));
        assertThat(ex.getMessage()).isEqualTo("Account is not active");
    }

    @Test
    void login_accountLocked() {
        User user = new User();
        user.setStatus("ACTIVE");
        user.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.of(user));

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "pw");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginReq, "127.0.0.1", "test-agent"));
        assertThat(ex.getMessage()).isEqualTo("Account is locked");
    }

    @Test
    void login_failedLoginIncrementsCount() {
        User user = new User();
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(4);
        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pw", user.getPasswordHash())).thenReturn(false);

        com.jobtracker.auth.dto.LoginRequest loginReq = new com.jobtracker.auth.dto.LoginRequest("test@example.com", "wrong_pw");
        assertThrows(IllegalArgumentException.class, () -> authService.login(loginReq, "127.0.0.1", "test-agent"));
        
        verify(userRepository, times(1)).save(user);
        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void refresh_invalidGraceWindowDecryption() throws Exception {
        com.jobtracker.auth.entity.RefreshToken rt = new com.jobtracker.auth.entity.RefreshToken();
        rt.setSupersededAt(java.time.Instant.now().minusSeconds(5));
        rt.setGraceWindowEncrypted("bad-encrypted");
        
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(tokenEncryptionService.decrypt("bad-encrypted")).thenThrow(new RuntimeException("Decryption failed"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.refresh("some-token", "127.0.0.1", "agent"));
        assertThat(ex.getMessage()).isEqualTo("Invalid refresh token");
        verify(refreshTokenRepository, times(1)).revokeFamily(any(), any());
    }

    @Test
    void logout_nullToken() {
        authService.logout(null);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void requestPasswordReset_rateLimited() {
        when(rateLimitService.isPasswordResetRateLimited("test@example.com")).thenReturn(true);
        authService.requestPasswordReset("test@example.com");
        verify(userRepository, never()).findByEmailNormalized(anyString());
    }

    @Test
    void requestPasswordReset_userNotActive() {
        User user = new User();
        user.setStatus("UNVERIFIED");
        when(rateLimitService.isPasswordResetRateLimited("test@example.com")).thenReturn(false);
        when(userRepository.findByEmailNormalized("test@example.com")).thenReturn(Optional.of(user));
        
        authService.requestPasswordReset("test@example.com");
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_tokenNotUpdated() {
        com.jobtracker.auth.entity.AuthToken token = new com.jobtracker.auth.entity.AuthToken();
        token.setId(UUID.randomUUID());
        token.setExpiresAt(java.time.Instant.now().plusSeconds(600));
        
        when(authTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(authTokenRepository.consumeToken(eq(token.getId()), any())).thenReturn(0);

        com.jobtracker.auth.dto.PasswordResetDto req = new com.jobtracker.auth.dto.PasswordResetDto("token", "newPw");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(req));
        assertThat(ex.getMessage()).isEqualTo("Token expired or already used");
    }

    @Test
    void verifyEmail_tokenNotUpdated() {
        com.jobtracker.auth.entity.AuthToken token = new com.jobtracker.auth.entity.AuthToken();
        token.setId(UUID.randomUUID());
        token.setExpiresAt(java.time.Instant.now().plusSeconds(600));
        
        when(authTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(authTokenRepository.consumeToken(eq(token.getId()), any())).thenReturn(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("token"));
        assertThat(ex.getMessage()).isEqualTo("Token expired or already used");
    }
}
