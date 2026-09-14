package com.jobtracker.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.auth.dto.*;
import com.jobtracker.auth.entity.AuthToken;
import com.jobtracker.auth.entity.RefreshToken;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.AuthTokenRepository;
import com.jobtracker.auth.repository.RefreshTokenRepository;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.common.config.CookieProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenEncryptionService tokenEncryptionService;
    private final RateLimitService rateLimitService;
    private final EmailService emailService;
    private final CookieProperties cookieProperties;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenEncryptionService tokenEncryptionService,
                       RateLimitService rateLimitService,
                       EmailService emailService,
                       CookieProperties cookieProperties,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenEncryptionService = tokenEncryptionService;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;
        this.cookieProperties = cookieProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String emailNormalized = req.email().trim().toLowerCase();
        
        if (userRepository.findByEmailNormalized(emailNormalized).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setEmail(req.email().trim());
        user.setEmailNormalized(emailNormalized);
        user.setName(req.name().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus("UNVERIFIED");
        user.setRole("USER");
        
        user = userRepository.save(user);

        AuthTokenWrapper authToken = createAuthToken(user.getId(), "EMAIL_VERIFY", 24 * 60 * 60);
        emailService.sendVerificationEmail(user.getId(), user.getEmail(), authToken.rawToken());

        return new RegisterResponse(user.getId(), user.getEmail(), user.getStatus());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = hashToken(rawToken);
        AuthToken token = authTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getConsumedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired or already used");
        }

        int updated = authTokenRepository.consumeToken(token.getId(), Instant.now());
        if (updated == 0) {
            throw new IllegalArgumentException("Token expired or already used");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if ("UNVERIFIED".equals(user.getStatus())) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public TokenPair login(LoginRequest req, String ipAddress, String userAgent) {
        String emailNormalized = req.email().trim().toLowerCase();
        
        if (rateLimitService.isLoginRateLimited(ipAddress, emailNormalized)) {
            throw new IllegalArgumentException("Too many login attempts");
        }

        User user = userRepository.findByEmailNormalized(emailNormalized)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Account is locked");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= 5) {
                user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
            throw new IllegalArgumentException("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return generateTokensForUser(user, ipAddress, userAgent);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        String tokenHash = hashToken(rawRefreshToken);
        Instant now = Instant.now();

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (oldToken.getSupersededAt() != null) {
            if (now.isBefore(oldToken.getSupersededAt().plusSeconds(10)) && oldToken.getGraceWindowEncrypted() != null) {
                try {
                    String decrypted = tokenEncryptionService.decrypt(oldToken.getGraceWindowEncrypted());
                    ObjectNode node = (ObjectNode) objectMapper.readTree(decrypted);
                    String r2 = node.get("r").asText();
                    String j2 = node.get("j").asText();
                    return new TokenPair(j2, 900, r2, cookieProperties.maxAgeSeconds());
                } catch (Exception e) {
                    // fallthrough to revoke
                }
            }
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), now);
            throw new IllegalArgumentException("Invalid refresh token");
        }

        if (oldToken.getRevokedAt() != null || oldToken.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // Generate new refresh token
        String newRawRefreshToken = generateSecureToken();
        String newHash = hashToken(newRawRefreshToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUserId(oldToken.getUserId());
        newToken.setFamilyId(oldToken.getFamilyId());
        newToken.setTokenHash(newHash);
        newToken.setExpiresAt(now.plusSeconds(cookieProperties.maxAgeSeconds()));
        newToken.setIpAddress(ipAddress);
        newToken.setUserAgent(userAgent);
        
        newToken = refreshTokenRepository.save(newToken);

        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active");
        }

        String newJwt = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        // Update old token
        oldToken.setRevokedAt(now);
        oldToken.setSupersededAt(now);
        oldToken.setReplacedBy(newToken.getId());

        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("r", newRawRefreshToken);
            node.put("j", newJwt);
            String encrypted = tokenEncryptionService.encrypt(objectMapper.writeValueAsString(node));
            oldToken.setGraceWindowEncrypted(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt grace window", e);
        }
        
        refreshTokenRepository.save(oldToken);

        return new TokenPair(newJwt, 900, newRawRefreshToken, cookieProperties.maxAgeSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        String tokenHash = hashToken(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        tokenOpt.ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String emailNormalized = email.trim().toLowerCase();
        if (rateLimitService.isPasswordResetRateLimited(emailNormalized)) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmailNormalized(emailNormalized);
        if (userOpt.isEmpty() || !"ACTIVE".equals(userOpt.get().getStatus())) {
            return;
        }

        User user = userOpt.get();
        AuthTokenWrapper token = createAuthToken(user.getId(), "PASSWORD_RESET", 3600);
        emailService.sendPasswordResetEmail(user.getEmail(), token.rawToken());
    }

    @Transactional
    public void resetPassword(PasswordResetDto req) {
        String tokenHash = hashToken(req.token());
        AuthToken token = authTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getConsumedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired or already used");
        }

        int updated = authTokenRepository.consumeToken(token.getId(), Instant.now());
        if (updated == 0) {
            throw new IllegalArgumentException("Token expired or already used");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setLockedUntil(null);
        user.setFailedLoginCount(0);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private TokenPair generateTokensForUser(User user, String ipAddress, String userAgent) {
        String rawRefreshToken = generateSecureToken();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(cookieProperties.maxAgeSeconds()));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent);
        
        refreshTokenRepository.save(refreshToken);

        String jwt = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenPair(jwt, 900, rawRefreshToken, cookieProperties.maxAgeSeconds());
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private AuthTokenWrapper createAuthToken(UUID userId, String type, long ttlSeconds) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        AuthToken authToken = new AuthToken();
        authToken.setUserId(userId);
        authToken.setTokenType(type);
        authToken.setTokenHash(tokenHash);
        authToken.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        
        authTokenRepository.save(authToken);
        
        return new AuthTokenWrapper(authToken, rawToken);
    }

    public record TokenPair(String accessToken, int expiresIn, String rawRefreshToken, long refreshMaxAge) {}
    
    private record AuthTokenWrapper(AuthToken entity, String rawToken) {}
}
