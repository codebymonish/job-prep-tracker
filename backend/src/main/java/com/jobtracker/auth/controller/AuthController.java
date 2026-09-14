package com.jobtracker.auth.controller;

import com.jobtracker.auth.dto.*;
import com.jobtracker.auth.service.AuthService;
import com.jobtracker.common.config.CookieProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieProperties cookieProperties;

    public AuthController(AuthService authService, CookieProperties cookieProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        
        AuthService.TokenPair tokens = authService.login(req, ip, userAgent);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(tokens.rawRefreshToken(), tokens.refreshMaxAge()).toString())
                .body(new LoginResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String rawRefreshToken,
            HttpServletRequest request) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String ip = getClientIp(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        AuthService.TokenPair tokens = authService.refresh(rawRefreshToken, ip, userAgent);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(tokens.rawRefreshToken(), tokens.refreshMaxAge()).toString())
                .body(new RefreshResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            authService.logout(rawRefreshToken);
        }
        
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .maxAge(0)
                .sameSite(cookieProperties.sameSite().attributeValue())
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/password-reset-request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto req) {
        authService.requestPasswordReset(req.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetDto req) {
        authService.resetPassword(req);
        return ResponseEntity.ok().build();
    }

    private ResponseCookie buildCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .maxAge(maxAgeSeconds)
                .sameSite(cookieProperties.sameSite().attributeValue())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
