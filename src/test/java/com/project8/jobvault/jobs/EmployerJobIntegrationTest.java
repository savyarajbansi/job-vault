package com.project8.jobvault.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "jobvault.security.jwt.secret=unit-test-secret-should-be-at-least-32-bytes-long",
        "jobvault.security.jwt.refresh-hash-secret=unit-test-refresh-secret-should-be-at-least-32-bytes-long",
        "jobvault.security.jwt.issuer=jobvault-test",
        "jobvault.security.jwt.access-token-minutes=5",
        "jobvault.security.jwt.refresh-token-days=30",
        "jobvault.security.jwt.refresh-token-bytes=32",
        "jobvault.security.jwt.csrf-token-bytes=16",
        "jobvault.security.jwt.max-sessions=5",
        "jobvault.security.cookies.refresh-token-name=refresh_token",
        "jobvault.security.cookies.csrf-token-name=csrf_token",
        "jobvault.security.cookies.same-site=Lax",
        "jobvault.security.cookies.secure=true",
        "jobvault.security.cookies.path=/api/auth"
})
class EmployerJobIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobRepository jobRepository;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockBean
    private ResumeStorageService resumeStorageService;

    private final ConcurrentMap<UUID, Job> jobsById = new ConcurrentHashMap<>();

    private UserAccount employerUser;
    private UserAccount otherEmployer;
    private UserAccount seekerUser;

    @BeforeEach
    void setUp() {
        jobsById.clear();

        Role employerRole = buildRole("EMPLOYER");
        Role seekerRole = buildRole("JOB_SEEKER");

        employerUser = buildUser("employer@example.com", employerRole);
        otherEmployer = buildUser("employer2@example.com", employerRole);
        seekerUser = buildUser("user@example.com", seekerRole);

        when(userAccountRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            if (employerUser.getId().equals(userId)) {
                return Optional.of(employerUser);
            }
            if (otherEmployer.getId().equals(userId)) {
                return Optional.of(otherEmployer);
            }
            if (seekerUser.getId().equals(userId)) {
                return Optional.of(seekerUser);
            }
            return Optional.empty();
        });

        when(jobRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID jobId = invocation.getArgument(0);
            return Optional.ofNullable(jobsById.get(jobId));
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            jobsById.put(job.getId(), job);
            return job;
        });
        when(jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.PUBLISHED))
                .thenAnswer(invocation -> jobsById.values().stream()
                        .filter(job -> job.getStatus() == JobStatus.PUBLISHED)
                        .toList());
        when(jobRepository.findByIdAndStatus(any(UUID.class), any(JobStatus.class)))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    JobStatus status = invocation.getArgument(1);
                    Job job = jobsById.get(jobId);
                    if (job == null || job.getStatus() != status) {
                        return Optional.empty();
                    }
                    return Optional.of(job);
                });
    }

    @Test
    void employerCanCreatePublishDisableJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/employer/jobs/{id}/disable", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void employerCanUpdateOwnJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(patch("/api/employer/jobs/{id}", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Senior Backend Engineer\",\"description\":\"Build APIs v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.description").value("Build APIs v2"));
    }

    @Test
    void nonEmployerIsDenied() throws Exception {
        mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void otherEmployerCannotModifyJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(otherEmployer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void seekerCannotPublishJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void publicJobsOnlyReturnPublished() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        Job published = buildJob(employerUser, JobStatus.PUBLISHED);
        jobsById.put(draft.getId(), draft);
        jobsById.put(published.getId(), published);

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(published.getId().toString()));
    }

    @Test
    void publicJobDetailsReturnNotFoundForDraft() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        jobsById.put(draft.getId(), draft);

        mockMvc.perform(get("/api/jobs/{id}", draft.getId()))
                .andExpect(status().isNotFound());
    }

    private UUID extractJobId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(node.path("id").asText());
    }

    private String issueToken(UserAccount user) {
        return jwtTokenService.issueAccessToken(user).token();
    }

    private Role buildRole(String name) {
        Role role = new TestRole();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }

    private UserAccount buildUser(String email, Role role) {
        UserAccount account = new TestUserAccount();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        account.getRoles().add(role);
        account.setEnabled(true);
        return account;
    }

    private Job buildJob(UserAccount employer, JobStatus status) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle("Title");
        job.setDescription("Desc");
        job.setStatus(status);
        return job;
    }

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }

    static final class TestJob extends Job {
    }
}
