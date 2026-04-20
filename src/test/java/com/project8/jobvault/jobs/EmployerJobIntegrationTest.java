package com.project8.jobvault.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import com.project8.jobvault.testing.DeterministicConcurrencyHarness;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.ArgumentMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.nullValue;
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

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockitoBean
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

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
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

        when(jobRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID jobId = invocation.getArgument(0);
            return Optional.ofNullable(jobsById.get(jobId));
        });
        when(jobRepository.save(nonNullArgument())).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            jobsById.put(job.getId(), job);
            return job;
        });
        when(jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE))
                .thenAnswer(invocation -> jobsById.values().stream()
                        .filter(job -> job.getStatus() == JobStatus.ACTIVE)
                        .toList());
        when(jobRepository.findByIdAndStatus(
                nonNullArgument(),
                nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    JobStatus status = invocation.getArgument(1);
                    Job job = jobsById.get(jobId);
                    if (job == null || job.getStatus() != status) {
                        return Optional.empty();
                    }
                    return Optional.of(job);
                });
        when(jobRepository.transitionDraftToActive(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UUID employerId = invocation.getArgument(1);
                    Instant publishedAt = invocation.getArgument(2);
                    Job job = jobsById.get(jobId);
                    if (job == null || job.getEmployer() == null || !employerId.equals(job.getEmployer().getId())) {
                        return 0;
                    }
                    if (job.getStatus() != JobStatus.DRAFT) {
                        return 0;
                    }
                    job.setStatus(JobStatus.ACTIVE);
                    job.setPublishedAt(publishedAt);
                    job.setDisabledAt(null);
                    jobsById.put(jobId, job);
                    return 1;
                });
        when(jobRepository.transitionActiveToDisabled(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UUID employerId = invocation.getArgument(1);
                    Instant disabledAt = invocation.getArgument(2);
                    Job job = jobsById.get(jobId);
                    if (job == null || job.getEmployer() == null || !employerId.equals(job.getEmployer().getId())) {
                        return 0;
                    }
                    if (job.getStatus() != JobStatus.ACTIVE) {
                        return 0;
                    }
                    job.setStatus(JobStatus.DISABLED);
                    job.setDisabledAt(disabledAt);
                    jobsById.put(jobId, job);
                    return 1;
                });
        when(jobRepository.transitionDisabledToActive(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UUID employerId = invocation.getArgument(1);
                    Instant publishedAt = invocation.getArgument(2);
                    Job job = jobsById.get(jobId);
                    if (job == null || job.getEmployer() == null || !employerId.equals(job.getEmployer().getId())) {
                        return 0;
                    }
                    synchronized (job) {
                        if (job.getStatus() != JobStatus.DISABLED) {
                            return 0;
                        }
                        if (job.getModerationAction() != null
                                && job.getModerationAction() != JobModerationAction.APPROVED) {
                            return 0;
                        }
                        job.setStatus(JobStatus.ACTIVE);
                        job.setPublishedAt(publishedAt);
                        job.setDisabledAt(null);
                        jobsById.put(jobId, job);
                        return 1;
                    }
                });
    }

    @Test
    void employerCanCreatePublishDisableJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/employer/jobs/{id}/disable", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void employerCanUpdateOwnJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobId = extractJobId(createResult);

        mockMvc.perform(patch("/api/employer/jobs/{id}", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"title\":\"Senior Backend Engineer\",\"description\":\"Build APIs v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.description").value("Build APIs v2"));
    }

    @Test
    void employerPublishClearsStaleDisabledAtForDraftJob() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        draft.setDisabledAt(Instant.parse("2026-04-19T09:00:00Z"));
        jobsById.put(draft.getId(), draft);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", draft.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disabledAt").value(nullValue()));

        Job saved = jobsById.get(draft.getId());
        assertEquals(JobStatus.ACTIVE, saved.getStatus());
        assertNull(saved.getDisabledAt());
    }

    @Test
    void nonEmployerIsDenied() throws Exception {
        mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void otherEmployerCannotModifyJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
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
                .contentType(MediaType.APPLICATION_JSON_VALUE)
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
    void unauthenticatedEmployerLifecycleRequestsReturnUnauthorized() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        jobsById.put(draft.getId(), draft);

        mockMvc.perform(post("/api/employer/jobs")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"title\":\"Backend Engineer\",\"description\":\"Build APIs\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", draft.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", draft.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void idempotentPublishAndDisableReturnConflictWithoutModerationMutation() throws Exception {
        Job active = buildJob(employerUser, JobStatus.ACTIVE);
        active.setModerationAction(JobModerationAction.APPROVED);
        active.setModerationReason("admin-approved");
        active.setModeratedAt(Instant.parse("2026-04-19T06:10:00Z"));
        active.setModeratedBy(otherEmployer);
        jobsById.put(active.getId(), active);

        mockMvc.perform(post("/api/employer/jobs/{id}/publish", active.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());

        Job unchangedAfterPublishConflict = jobsById.get(active.getId());
        assertEquals(JobStatus.ACTIVE, unchangedAfterPublishConflict.getStatus());
        assertEquals(JobModerationAction.APPROVED, unchangedAfterPublishConflict.getModerationAction());
        assertEquals("admin-approved", unchangedAfterPublishConflict.getModerationReason());
        assertEquals(Instant.parse("2026-04-19T06:10:00Z"), unchangedAfterPublishConflict.getModeratedAt());
        assertEquals(otherEmployer.getId(), unchangedAfterPublishConflict.getModeratedBy().getId());

        mockMvc.perform(post("/api/employer/jobs/{id}/disable", active.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/employer/jobs/{id}/disable", active.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());

        Job unchangedAfterDisableConflict = jobsById.get(active.getId());
        assertEquals(JobStatus.DISABLED, unchangedAfterDisableConflict.getStatus());
        assertEquals(JobModerationAction.APPROVED, unchangedAfterDisableConflict.getModerationAction());
        assertEquals("admin-approved", unchangedAfterDisableConflict.getModerationReason());
        assertEquals(Instant.parse("2026-04-19T06:10:00Z"), unchangedAfterDisableConflict.getModeratedAt());
        assertEquals(otherEmployer.getId(), unchangedAfterDisableConflict.getModeratedBy().getId());
    }

    @Test
    void publicJobsOnlyReturnActive() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        Job published = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(draft.getId(), draft);
        jobsById.put(published.getId(), published);

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(published.getId().toString()));
    }

    @Test
    void publicJobDetailsReturnNotFoundForDraft() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT);
        jobsById.put(draft.getId(), draft);

        mockMvc.perform(get("/api/jobs/{id}", draft.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicJobDetailsReturnNotFoundForMissingJob() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void employerCanReactivateDisabledJobWhenModerationActionIsNull() throws Exception {
        Job disabled = buildJob(employerUser, JobStatus.DISABLED);
        disabled.setDisabledAt(Instant.parse("2026-04-19T07:30:00Z"));
        jobsById.put(disabled.getId(), disabled);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", disabled.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disabledAt").value(nullValue()));

        Job saved = jobsById.get(disabled.getId());
        assertEquals(JobStatus.ACTIVE, saved.getStatus());
        assertNull(saved.getModerationAction());
        assertNull(saved.getDisabledAt());
    }

    @Test
    void employerCanReactivateDisabledJobWhenModerationActionIsApproved() throws Exception {
        Job disabled = buildJob(employerUser, JobStatus.DISABLED);
        disabled.setModerationAction(JobModerationAction.APPROVED);
        disabled.setDisabledAt(Instant.parse("2026-04-19T07:45:00Z"));
        jobsById.put(disabled.getId(), disabled);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", disabled.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disabledAt").value(nullValue()));

        Job saved = jobsById.get(disabled.getId());
        assertEquals(JobModerationAction.APPROVED, saved.getModerationAction());
        assertNull(saved.getDisabledAt());
    }

    @Test
    void employerCannotReactivateWhenModerationActionRequiresAdminApproval() throws Exception {
        Job rejected = buildJob(employerUser, JobStatus.DISABLED);
        rejected.setModerationAction(JobModerationAction.REJECTED);
        jobsById.put(rejected.getId(), rejected);

        Job disabledByAdmin = buildJob(employerUser, JobStatus.DISABLED);
        disabledByAdmin.setModerationAction(JobModerationAction.DISABLED);
        jobsById.put(disabledByAdmin.getId(), disabledByAdmin);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", rejected.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", disabledByAdmin.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());

        assertEquals(JobStatus.DISABLED, jobsById.get(rejected.getId()).getStatus());
        assertEquals(JobModerationAction.REJECTED, jobsById.get(rejected.getId()).getModerationAction());
        assertEquals(JobStatus.DISABLED, jobsById.get(disabledByAdmin.getId()).getStatus());
        assertEquals(JobModerationAction.DISABLED, jobsById.get(disabledByAdmin.getId()).getModerationAction());
    }

    @Test
    void adminApprovalPrecedenceBlocksThenAllowsEmployerReactivation() throws Exception {
        Job disabled = buildJob(employerUser, JobStatus.DISABLED);
        disabled.setModerationAction(JobModerationAction.REJECTED);
        disabled.setModerationReason("policy violation");
        disabled.setDisabledAt(Instant.parse("2026-04-19T08:00:00Z"));
        jobsById.put(disabled.getId(), disabled);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", disabled.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());

        Job blockedState = jobsById.get(disabled.getId());
        assertEquals(JobStatus.DISABLED, blockedState.getStatus());
        assertEquals(JobModerationAction.REJECTED, blockedState.getModerationAction());
        assertEquals("policy violation", blockedState.getModerationReason());

        blockedState.setModerationAction(JobModerationAction.APPROVED);
        blockedState.setModerationReason(null);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", disabled.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disabledAt").value(nullValue()));

        Job reactivated = jobsById.get(disabled.getId());
        assertEquals(JobStatus.ACTIVE, reactivated.getStatus());
        assertEquals(JobModerationAction.APPROVED, reactivated.getModerationAction());
        assertNull(reactivated.getModerationReason());
    }

    @Test
    void roleOwnershipAndStatePrecedenceIs403Then404Then409() throws Exception {
        Job blocked = buildJob(employerUser, JobStatus.DISABLED);
        blocked.setModerationAction(JobModerationAction.REJECTED);
        jobsById.put(blocked.getId(), blocked);

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", blocked.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"));

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", blocked.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(otherEmployer)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", blocked.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentReactivateHasSingleWinnerAndSingleConflict() throws Exception {
        UUID jobId = DeterministicConcurrencyHarness.withBootstrapRetry("employer-reactivate-bootstrap", 2, () -> {
            Job disabled = buildJob(employerUser, JobStatus.DISABLED);
            disabled.setModerationAction(JobModerationAction.APPROVED);
            jobsById.put(disabled.getId(), disabled);
            return disabled.getId();
        });

        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "employer-reactivate-race",
                "reactivate-a",
                () -> performReactivate(jobId),
                "reactivate-b",
                () -> performReactivate(jobId));

        DeterministicConcurrencyHarness.assertSingleWinnerAndConflict("employer-reactivate-race", result, 200);

        Job updated = jobsById.get(jobId);
        assertEquals(JobStatus.ACTIVE, updated.getStatus());
        assertEquals(JobModerationAction.APPROVED, updated.getModerationAction());
    }

    private UUID extractJobId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(node.path("id").asText());
    }

    private DeterministicConcurrencyHarness.ContentionResponse performReactivate(UUID jobId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employer/jobs/{id}/reactivate", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andReturn();
        return DeterministicConcurrencyHarness.ContentionResponse.of(
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private String issueToken(UserAccount user) {
        return jwtTokenService.issueAccessToken(user).token();
    }

    @NonNull
    @SuppressWarnings("null")
    private static <T> T nonNullArgument() {
        return ArgumentMatchers.notNull();
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
