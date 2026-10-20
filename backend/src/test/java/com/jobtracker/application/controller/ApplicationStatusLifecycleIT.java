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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.data.redis.url=")
@Import(TestcontainersConfiguration.class)
class ApplicationStatusLifecycleIT {

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
        user.setEmail("lifecycle-user@example.com");
        user.setEmailNormalized("lifecycle-user@example.com");
        user.setName("Lifecycle User");
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

    private Application createApp(ApplicationStatus status) {
        Application app = new Application();
        app.setUser(user);
        app.setCompany("Stripe");
        app.setRoleTitle("Engineer");
        app.setStatus(status);
        app.setAppliedDate(LocalDate.now());
        app.setDeleted(false);
        return applicationRepository.save(app);
    }

    @Test
    void validLifecycle_appliedToInterviewingToOffer() throws Exception {
        Application app = createApp(ApplicationStatus.APPLIED);

        // Step 1: APPLIED -> INTERVIEWING
        UpdateApplicationStatusRequest req1 = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);
        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INTERVIEWING")))
                .andExpect(jsonPath("$.version", is(1)));

        // Step 2: INTERVIEWING -> OFFER
        UpdateApplicationStatusRequest req2 = new UpdateApplicationStatusRequest(ApplicationStatus.OFFER, 1);
        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OFFER")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void illegalDirectJump_appliedToOffer_returns400() throws Exception {
        Application app = createApp(ApplicationStatus.APPLIED);

        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.OFFER, 0);
        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Status Transition")));
    }

    @Test
    void terminalStatus_offerCannotTransitionFurther() throws Exception {
        Application app = createApp(ApplicationStatus.OFFER);

        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);
        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void terminalStatus_rejectedCannotTransitionFurther() throws Exception {
        Application app = createApp(ApplicationStatus.REJECTED);

        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.APPLIED, 0);
        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
