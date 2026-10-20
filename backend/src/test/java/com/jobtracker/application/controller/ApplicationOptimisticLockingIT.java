package com.jobtracker.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.application.dto.UpdateApplicationStatusRequest;
import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.repository.ApplicationRepository;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.data.redis.url=")
@Import(TestcontainersConfiguration.class)
class ApplicationOptimisticLockingIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupDatabase();

        user = new User();
        user.setEmail("lock-user@example.com");
        user.setEmailNormalized("lock-user@example.com");
        user.setName("Lock User");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus("ACTIVE");
        user.setRole("USER");
        user = userRepository.save(user);

        token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE applications, audit_log, users CASCADE");
    }

    @Test
    void optimisticLocking_staleUpdateReturns409() throws Exception {
        Application app = new Application();
        app.setUser(user);
        app.setCompany("Databricks");
        app.setRoleTitle("Distributed Systems Engineer");
        app.setStatus(ApplicationStatus.APPLIED);
        app.setAppliedDate(LocalDate.now());
        app.setDeleted(false);
        app = applicationRepository.save(app);

        // 1. Valid update with current version 0 -> 200
        UpdateApplicationStatusRequest firstUpdate =
                new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);

        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INTERVIEWING")))
                .andExpect(jsonPath("$.version", is(1)));

        // 2. Second update with stale version 0 -> 409 Conflict
        UpdateApplicationStatusRequest staleUpdate =
                new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED, 0);

        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", containsString("/stale-update")))
                .andExpect(jsonPath("$.title", is("Stale Update")));

        // 3. Update with correct version 1 -> 200
        UpdateApplicationStatusRequest correctUpdate =
                new UpdateApplicationStatusRequest(ApplicationStatus.OFFER, 1);

        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OFFER")))
                .andExpect(jsonPath("$.version", is(2)));
    }
}
