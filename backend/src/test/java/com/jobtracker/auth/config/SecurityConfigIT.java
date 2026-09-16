package com.jobtracker.auth.config;

import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.profiles.active=test", "app.auth.frontend-base-url=http://localhost:4200"})
@Import(TestcontainersConfiguration.class)
class SecurityConfigIT {

    private MockMvc mockMvc;

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @BeforeEach
    void setup() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Autowired
    private JwtService jwtService;

    @Test
    void publicEndpointWithoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public-test-endpoint"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/protected-test-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidJwt_shouldReturn200() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "test@example.com", "USER");
        
        mockMvc.perform(get("/api/v1/protected-test-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointWithUserRole_shouldReturn403() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", "USER");

        mockMvc.perform(get("/api/v1/admin-test-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointWithAdminRole_shouldReturn200() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "admin@example.com", "ADMIN");

        mockMvc.perform(get("/api/v1/admin-test-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightFromAllowedOrigin_shouldReturn200AndHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/public-test-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void corsPreflightFromDisallowedOrigin_shouldNotHaveCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/public-test-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://malicious.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeaders_shouldBePresent() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public-test-endpoint").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; sandbox"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Autowired
    private com.jobtracker.auth.repository.UserRepository userRepository;

    @Test
    void pendingDeletionUser_canAccessLogoutAndRefresh() throws Exception {
        com.jobtracker.auth.entity.User user = new com.jobtracker.auth.entity.User();
        user.setEmail("pending1@example.com");
        user.setEmailNormalized("pending1@example.com");
        user.setName("Pending User");
        user.setPasswordHash("hashed");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), "USER");
        
        mockMvc.perform(get("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/v1/account/details").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void pendingDeletionUser_cannotAccessBusinessRoutes() throws Exception {
        com.jobtracker.auth.entity.User user = new com.jobtracker.auth.entity.User();
        user.setEmail("pending2@example.com");
        user.setEmailNormalized("pending2@example.com");
        user.setName("Pending User");
        user.setPasswordHash("hashed");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), "USER");
        
        mockMvc.perform(get("/api/v1/protected-test-endpoint").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account Pending Deletion"));
    }

}

@RestController
class TestSecurityController {

    @GetMapping("/api/v1/auth/public-test-endpoint")
    public String publicEndpoint() {
        return "OK";
    }

    @GetMapping("/api/v1/protected-test-endpoint")
    public String protectedEndpoint() {
        return "OK";
    }

    @GetMapping("/api/v1/admin-test-endpoint")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminEndpoint() {
        return "OK";
    }

    @GetMapping("/api/v1/auth/refresh")
    public String refreshEndpoint() {
        return "OK";
    }

    @GetMapping("/api/v1/auth/logout")
    public String logoutEndpoint() {
        return "OK";
    }

    @GetMapping("/api/v1/account/details")
    public String accountEndpoint() {
        return "OK";
    }
}
