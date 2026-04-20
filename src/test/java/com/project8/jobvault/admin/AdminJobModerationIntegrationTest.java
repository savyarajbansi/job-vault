package com.project8.jobvault.admin;

import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobModerationAction;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
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
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
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
class AdminJobModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockitoBean
    private ResumeStorageService resumeStorageService;

    private final ConcurrentMap<UUID, UserAccount> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Job> jobsById = new ConcurrentHashMap<>();

    private UserAccount adminUser;
    private UserAccount employerUser;
    private UserAccount seekerUser;

    @BeforeEach
    void setUp() {
        usersById.clear();
        jobsById.clear();

        Role adminRole = buildRole("ADMIN");
        Role employerRole = buildRole("EMPLOYER");
        Role seekerRole = buildRole("JOB_SEEKER");

        adminUser = buildUser("admin@example.com", adminRole);
        employerUser = buildUser("employer@example.com", employerRole);
        seekerUser = buildUser("user@example.com", seekerRole);

        usersById.put(adminUser.getId(), adminUser);
        usersById.put(employerUser.getId(), employerUser);
        usersById.put(seekerUser.getId(), seekerUser);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return Optional.ofNullable(usersById.get(userId));
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
        when(jobRepository.approveForAdmin(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument())).thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UserAccount moderatedBy = invocation.getArgument(1);
                    Instant moderatedAt = invocation.getArgument(2);
                    Instant publishedAt = invocation.getArgument(3);
                    Job job = jobsById.get(jobId);
                    if (job == null) {
                        return 0;
                    }
                    synchronized (job) {
                        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.DISABLED) {
                            return 0;
                        }
                        job.setStatus(JobStatus.ACTIVE);
                        job.setModerationAction(JobModerationAction.APPROVED);
                        job.setModerationReason(null);
                        job.setModeratedBy(moderatedBy);
                        job.setModeratedAt(moderatedAt);
                        job.setPublishedAt(publishedAt);
                        job.setDisabledAt(null);
                        return 1;
                    }
                });
        when(jobRepository.moderateActiveToDisabled(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument())).thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    JobModerationAction action = invocation.getArgument(1);
                    String reason = invocation.getArgument(2);
                    UserAccount moderatedBy = invocation.getArgument(3);
                    Instant moderatedAt = invocation.getArgument(4);
                    Instant disabledAt = invocation.getArgument(5);
                    Job job = jobsById.get(jobId);
                    if (job == null) {
                        return 0;
                    }
                    synchronized (job) {
                        if (job.getStatus() != JobStatus.ACTIVE) {
                            return 0;
                        }
                        job.setStatus(JobStatus.DISABLED);
                        job.setModerationAction(action);
                        job.setModerationReason(reason);
                        job.setModeratedBy(moderatedBy);
                        job.setModeratedAt(moderatedAt);
                        job.setDisabledAt(disabledAt);
                        return 1;
                    }
                });
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void nonAdminIsDeniedRegardlessOfJobExistence() throws Exception {
        UUID missingJobId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/jobs/{id}/approve", missingJobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void nonAdminIsDeniedForRejectAndDisableRegardlessOfJobExistence() throws Exception {
        UUID missingJobId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", missingJobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Policy violation\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));

        mockMvc.perform(post("/api/admin/jobs/{id}/disable", missingJobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Policy violation\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void adminCanApproveDraftJobAndSetModerationFields() throws Exception {
        Job draftJob = buildJob(JobStatus.DRAFT);
        jobsById.put(draftJob.getId(), draftJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/approve", draftJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Job updated = jobsById.get(draftJob.getId());
        assertEquals(JobStatus.ACTIVE, updated.getStatus());
        assertEquals(JobModerationAction.APPROVED, updated.getModerationAction());
        assertNotNull(updated.getModeratedAt());
        assertEquals(adminUser.getId(), updated.getModeratedBy().getId());
        assertNull(updated.getModerationReason());
    }

    @Test
    void adminCanApproveDisabledJob() throws Exception {
        Job disabledJob = buildJob(JobStatus.DISABLED);
        disabledJob.setModerationAction(JobModerationAction.DISABLED);
        disabledJob.setModerationReason("old reason");
        disabledJob.setDisabledAt(Instant.parse("2026-04-19T10:15:30Z"));
        jobsById.put(disabledJob.getId(), disabledJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/approve", disabledJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disabledAt").value(nullValue()));

        Job updated = jobsById.get(disabledJob.getId());
        assertEquals(JobStatus.ACTIVE, updated.getStatus());
        assertEquals(JobModerationAction.APPROVED, updated.getModerationAction());
        assertNull(updated.getModerationReason());
        assertNull(updated.getDisabledAt());
    }

    @Test
    void adminCanRejectActiveJobWithReason() throws Exception {
        Job activeJob = buildJob(JobStatus.ACTIVE);
        jobsById.put(activeJob.getId(), activeJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", activeJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        Job updated = jobsById.get(activeJob.getId());
        assertEquals(JobStatus.DISABLED, updated.getStatus());
        assertEquals(JobModerationAction.REJECTED, updated.getModerationAction());
        assertEquals("Policy violation", updated.getModerationReason());
        assertNotNull(updated.getModeratedAt());
        assertEquals(adminUser.getId(), updated.getModeratedBy().getId());
    }

    @Test
    void adminCanDisableActiveJobWithReason() throws Exception {
        Job activeJob = buildJob(JobStatus.ACTIVE);
        jobsById.put(activeJob.getId(), activeJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/disable", activeJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Temporarily suspended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        Job updated = jobsById.get(activeJob.getId());
        assertEquals(JobStatus.DISABLED, updated.getStatus());
        assertEquals(JobModerationAction.DISABLED, updated.getModerationAction());
        assertEquals("Temporarily suspended", updated.getModerationReason());
    }

    @Test
    void rejectAndDisableRequireNonBlankModerationReason() throws Exception {
        Job activeJob = buildJob(JobStatus.ACTIVE);
        jobsById.put(activeJob.getId(), activeJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", activeJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.moderationReason").exists());

        mockMvc.perform(post("/api/admin/jobs/{id}/disable", activeJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.moderationReason").exists());
    }

    @Test
    void missingJobReturnsNotFoundForAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/{id}/approve", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingJobReturnsNotFoundForRejectAndDisableBeforeConflict() throws Exception {
        UUID missingJobId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", missingJobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Policy violation\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/jobs/{id}/disable", missingJobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Policy violation\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidOrIdempotentActionsReturnConflictWithoutMutation() throws Exception {
        Job activeJob = buildJob(JobStatus.ACTIVE);
        activeJob.setModerationAction(null);
        activeJob.setModerationReason("existing reason");
        activeJob.setModeratedAt(Instant.parse("2026-04-17T08:30:00Z"));
        activeJob.setModeratedBy(adminUser);
        jobsById.put(activeJob.getId(), activeJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/approve", activeJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isConflict());

        Job unchanged = jobsById.get(activeJob.getId());
        assertEquals(JobStatus.ACTIVE, unchanged.getStatus());
        assertNull(unchanged.getModerationAction());
        assertEquals("existing reason", unchanged.getModerationReason());
        assertEquals(Instant.parse("2026-04-17T08:30:00Z"), unchanged.getModeratedAt());

        Job draftJob = buildJob(JobStatus.DRAFT);
        jobsById.put(draftJob.getId(), draftJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", draftJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"Not allowed\"}"))
                .andExpect(status().isConflict());

        Job draftUnchanged = jobsById.get(draftJob.getId());
        assertEquals(JobStatus.DRAFT, draftUnchanged.getStatus());
        assertNull(draftUnchanged.getModerationAction());
    }

    @Test
    void idempotentRejectAndDisableReturnConflictWithoutMutation() throws Exception {
        Job rejectedJob = buildJob(JobStatus.DISABLED);
        rejectedJob.setModerationAction(JobModerationAction.REJECTED);
        rejectedJob.setModerationReason("already rejected");
        rejectedJob.setModeratedAt(Instant.parse("2026-04-17T08:30:00Z"));
        rejectedJob.setModeratedBy(adminUser);
        jobsById.put(rejectedJob.getId(), rejectedJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/reject", rejectedJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"new reason\"}"))
                .andExpect(status().isConflict());

        Job rejectedUnchanged = jobsById.get(rejectedJob.getId());
        assertEquals(JobStatus.DISABLED, rejectedUnchanged.getStatus());
        assertEquals(JobModerationAction.REJECTED, rejectedUnchanged.getModerationAction());
        assertEquals("already rejected", rejectedUnchanged.getModerationReason());
        assertEquals(Instant.parse("2026-04-17T08:30:00Z"), rejectedUnchanged.getModeratedAt());

        Job disabledJob = buildJob(JobStatus.DISABLED);
        disabledJob.setModerationAction(JobModerationAction.DISABLED);
        disabledJob.setModerationReason("already disabled");
        disabledJob.setModeratedAt(Instant.parse("2026-04-17T08:31:00Z"));
        disabledJob.setModeratedBy(adminUser);
        jobsById.put(disabledJob.getId(), disabledJob);

        mockMvc.perform(post("/api/admin/jobs/{id}/disable", disabledJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"new reason\"}"))
                .andExpect(status().isConflict());

        Job disabledUnchanged = jobsById.get(disabledJob.getId());
        assertEquals(JobStatus.DISABLED, disabledUnchanged.getStatus());
        assertEquals(JobModerationAction.DISABLED, disabledUnchanged.getModerationAction());
        assertEquals("already disabled", disabledUnchanged.getModerationReason());
        assertEquals(Instant.parse("2026-04-17T08:31:00Z"), disabledUnchanged.getModeratedAt());
    }

    @Test
    void concurrentDisableHasSingleWinnerAndSingleConflict() throws Exception {
        UUID jobId = DeterministicConcurrencyHarness.withBootstrapRetry("admin-disable-bootstrap", 2, () -> {
            Job activeJob = buildJob(JobStatus.ACTIVE);
            jobsById.put(activeJob.getId(), activeJob);
            return activeJob.getId();
        });

        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "admin-disable-race",
                "disable-a",
                () -> performDisable(jobId, "policy"),
                "disable-b",
                () -> performDisable(jobId, "policy"));

        DeterministicConcurrencyHarness.assertSingleWinnerAndConflict("admin-disable-race", result, 200);

        Job updated = jobsById.get(jobId);
        assertEquals(JobStatus.DISABLED, updated.getStatus());
        assertEquals(JobModerationAction.DISABLED, updated.getModerationAction());
        assertEquals("policy", updated.getModerationReason());
    }

    private DeterministicConcurrencyHarness.ContentionResponse performDisable(UUID jobId, String reason)
            throws Exception {
        var response = mockMvc.perform(post("/api/admin/jobs/{id}/disable", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"moderationReason\":\"" + reason + "\"}"))
                .andReturn()
                .getResponse();
        return DeterministicConcurrencyHarness.ContentionResponse.of(
                response.getStatus(),
                response.getContentAsString());
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

    private Job buildJob(JobStatus status) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employerUser);
        job.setTitle("Role");
        job.setDescription("Description");
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