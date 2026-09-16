package com.jobtracker.auth.controller;

import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
class PendingDeletionGateIT {

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
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void pendingDeletionUser_accessingApplications_shouldReturn403() throws Exception {
        User user = new User();
        user.setEmail("pending1@example.com");
        user.setEmailNormalized("PENDING1@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setName("Pending User");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingDeletionUser_accessingAccount_shouldPassFilterAndReturn404() throws Exception {
        User user = new User();
        user.setEmail("pending2@example.com");
        user.setEmailNormalized("PENDING2@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setName("Pending User");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(get("/api/v1/account/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound()); 
    }

    @Test
    void pendingDeletionUser_accessingRefresh_shouldPassFilterAndReturn404() throws Exception {
        User user = new User();
        user.setEmail("pending3@example.com");
        user.setEmailNormalized("PENDING3@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setName("Pending User");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        // We use POST since refresh is a POST endpoint.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized()); // Expect 401 Unauthorized because the endpoint exists but we have no tokenshouldn't be 403 from PendingDeletion
    }

    @Test
    void pendingDeletionUser_accessingLogout_shouldPassFilterAndReturn404() throws Exception {
        User user = new User();
        user.setEmail("pending4@example.com");
        user.setEmailNormalized("PENDING4@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setName("Pending User");
        user.setStatus("PENDING_DELETION");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent()); 
    }

    @Test
    void activeUser_accessingApplications_shouldPassFilterAndReturn404() throws Exception {
        User user = new User();
        user.setEmail("active@example.com");
        user.setEmailNormalized("ACTIVE@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setName("Active User");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound()); 
    }
}
