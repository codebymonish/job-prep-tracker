package com.jobtracker.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.TestcontainersConfiguration;
import com.jobtracker.application.dto.CreateApplicationRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.data.redis.url=")
@Import(TestcontainersConfiguration.class)
class ApplicationControllerIT {

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

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupDatabase();

        userA = createUser("user-a@example.com", "User A");
        userB = createUser("user-b@example.com", "User B");

        tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), userA.getRole());
        tokenB = jwtService.generateToken(userB.getId(), userB.getEmail(), userB.getRole());
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE applications, audit_log, users CASCADE");
    }

    private User createUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setEmailNormalized(email.toLowerCase());
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus("ACTIVE");
        user.setRole("USER");
        return userRepository.save(user);
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listApplications_emptyForNewUser_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void createApplication_success_returns201() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest(
                "Acme Corp", "Backend Engineer", "Role details", LocalDate.now().minusDays(1));

        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.company", is("Acme Corp")))
                .andExpect(jsonPath("$.roleTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.jdText", is("Role details")))
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.version", is(0)))
                .andReturn();

        // Verify audit log row was written
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'CREATE' AND entity_type = 'APPLICATION'", Integer.class);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void createApplication_nullAppliedDate_defaultsToToday() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest(
                "Acme Corp", "Backend Engineer", null, null);

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedDate", is(LocalDate.now().toString())));
    }

    @Test
    void createApplication_futureDate_returns400() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest(
                "Acme Corp", "Backend Engineer", null, LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createApplication_emptyCompany_returns400() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest(
                "", "Backend Engineer", null, LocalDate.now());

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listApplications_excludesJdTextAndRespectsStatusFilter() throws Exception {
        createAppDirect(userA, "Google", "SWE", "Long JD text", ApplicationStatus.APPLIED);
        createAppDirect(userA, "Meta", "SWE", "Long JD text", ApplicationStatus.INTERVIEWING);

        // List all for userA: jdText should not be present in summary
        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].jdText").doesNotExist())
                .andExpect(jsonPath("$.content[1].jdText").doesNotExist());

        // Status filter: only INTERVIEWING
        mockMvc.perform(get("/api/v1/applications?status=INTERVIEWING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].company", is("Meta")));
    }

    @Test
    void getApplication_userIsolation_returns404ForOtherUser() throws Exception {
        Application appA = createAppDirect(userA, "Netflix", "Engineer", "JD", ApplicationStatus.APPLIED);

        // User A gets it: 200 with jdText
        mockMvc.perform(get("/api/v1/applications/" + appA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company", is("Netflix")))
                .andExpect(jsonPath("$.jdText", is("JD")));

        // User B tries to get User A's application: 404
        mockMvc.perform(get("/api/v1/applications/" + appA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", containsString("/not-found")));
    }

    @Test
    void updateStatus_success_returns200() throws Exception {
        Application app = createAppDirect(userA, "Amazon", "SDE", "JD", ApplicationStatus.APPLIED);
        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);

        mockMvc.perform(patch("/api/v1/applications/" + app.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INTERVIEWING")))
                .andExpect(jsonPath("$.version", is(1)));

        // Audit log written
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'STATUS_CHANGE' AND entity_id = ?",
                Integer.class, app.getId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void deleteApplication_softDeletes_rowRemainsWithIsDeletedTrue() throws Exception {
        Application app = createAppDirect(userA, "Apple", "iOS Dev", "JD", ApplicationStatus.APPLIED);

        mockMvc.perform(delete("/api/v1/applications/" + app.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // Subsequent GET returns 404
        mockMvc.perform(get("/api/v1/applications/" + app.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // In DB, row still exists with is_deleted = true
        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM applications WHERE id = ?", Boolean.class, app.getId());
        assertThat(isDeleted).isTrue();

        // Audit log written
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'DELETE' AND entity_id = ?",
                Integer.class, app.getId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void getStats_returnsCorrectCountsPerStatus() throws Exception {
        createAppDirect(userA, "Google", "SWE", null, ApplicationStatus.APPLIED);
        createAppDirect(userA, "Meta",   "SWE", null, ApplicationStatus.APPLIED);
        createAppDirect(userA, "Amazon", "SDE", null, ApplicationStatus.INTERVIEWING);
        createAppDirect(userA, "Apple",  "iOS", null, ApplicationStatus.OFFER);

        // userB's app — must NOT appear in userA's stats
        createAppDirect(userB, "Netflix", "Eng", null, ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/applications/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total",        is(4)))
                .andExpect(jsonPath("$.applied",      is(2)))
                .andExpect(jsonPath("$.interviewing", is(1)))
                .andExpect(jsonPath("$.offer",        is(1)))
                .andExpect(jsonPath("$.rejected",     is(0)))
                .andExpect(jsonPath("$.withdrawn",    is(0)));
    }

    @Test
    void getStats_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/applications/stats"))
                .andExpect(status().isUnauthorized());
    }

    private Application createAppDirect(User user, String company, String role, String jd, ApplicationStatus status) {
        Application app = new Application();
        app.setUser(user);
        app.setCompany(company);
        app.setRoleTitle(role);
        app.setJdText(jd);
        app.setStatus(status);
        app.setAppliedDate(LocalDate.now());
        app.setDeleted(false);
        return applicationRepository.save(app);
    }
}
