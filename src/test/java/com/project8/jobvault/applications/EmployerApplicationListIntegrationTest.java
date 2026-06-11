package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class EmployerApplicationListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockitoBean
    private ResumeStorageService resumeStorageService;

    @MockitoBean
    private NotificationService notificationService;

    private final ConcurrentMap<UUID, Job> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JobApplication> applicationsById = new ConcurrentHashMap<>();

    private UserAccount employerUser;
    private UserAccount otherEmployerUser;
    private UserAccount seekerUser;

    @BeforeEach
    void setUp() {
        jobsById.clear();
        applicationsById.clear();

        Role employerRole = buildRole("EMPLOYER");
        Role seekerRole = buildRole("JOB_SEEKER");

        employerUser = buildUser("employer@example.com", employerRole);
        otherEmployerUser = buildUser("other-employer@example.com", employerRole);
        seekerUser = buildUser("seeker@example.com", seekerRole);

        when(userAccountRepository.findById(nonNullArgument(UUID.class))).thenAnswer(invocation -> {
            UUID userId = Objects.requireNonNull(invocation.getArgument(0, UUID.class), "userId");
            if (employerUser.getId().equals(userId)) return Optional.of(employerUser);
            if (otherEmployerUser.getId().equals(userId)) return Optional.of(otherEmployerUser);
            if (seekerUser.getId().equals(userId)) return Optional.of(seekerUser);
            return Optional.empty();
        });

        when(jobRepository.findById(nonNullArgument(UUID.class))).thenAnswer(invocation -> {
            UUID jobId = Objects.requireNonNull(invocation.getArgument(0, UUID.class), "jobId");
            return Optional.ofNullable(jobsById.get(jobId));
        });

        when(jobApplicationRepository.findAllByJobIdAndJobEmployerIdOrderBySubmittedAtDesc(
                nonNullArgument(UUID.class),
                nonNullArgument(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID jobId = Objects.requireNonNull(invocation.getArgument(0, UUID.class), "jobId");
                    UUID employerId = Objects.requireNonNull(invocation.getArgument(1, UUID.class), "employerId");
                    return applicationsById.values().stream()
                            .filter(app -> app.getJob() != null && jobId.equals(app.getJob().getId()))
                            .filter(app -> app.getJob().getEmployer() != null
                                    && employerId.equals(app.getJob().getEmployer().getId()))
                            .sorted((a, b) -> {
                                if (a.getSubmittedAt() != null && b.getSubmittedAt() != null) {
                                    return b.getSubmittedAt().compareTo(a.getSubmittedAt());
                                }
                                return a.getId().compareTo(b.getId());
                            })
                            .toList();
                });
    }

    @Test
    void employerCanListApplicationsForOwnJob() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED,
                Instant.parse("2026-05-01T10:00:00Z"));
        JobApplication underReview = buildApplication(job, seekerUser, ApplicationStatus.UNDER_REVIEW,
                Instant.parse("2026-05-02T10:00:00Z"));
        applicationsById.put(submitted.getId(), submitted);
        applicationsById.put(underReview.getId(), underReview);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listApplicationsResponseIncludesExpectedFields() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        JobApplication application = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED,
                Instant.parse("2026-05-10T09:30:00Z"));
        applicationsById.put(application.getId(), application);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(application.getId().toString()))
                .andExpect(jsonPath("$[0].jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$[0].seekerId").value(seekerUser.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].submittedAt").isNotEmpty());
    }

    @Test
    void listApplicationsReturnsEmptyArrayWhenNoApplicationsExist() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listApplicationsReturnsDraftApplications() throws Exception {
        // DRAFT applications are included — the employer sees the full picture
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        JobApplication draft = buildApplication(job, seekerUser, ApplicationStatus.DRAFT, null);
        applicationsById.put(draft.getId(), draft);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void listApplicationsReturnsNotFoundForMissingJob() throws Exception {
        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listApplicationsReturnsNotFoundForJobOwnedByOtherEmployer() throws Exception {
        // Ownership check: other employer's job is invisible, not forbidden
        Job otherJob = buildJob(otherEmployerUser, JobStatus.ACTIVE);
        jobsById.put(otherJob.getId(), otherJob);

        JobApplication application = buildApplication(otherJob, seekerUser,
                ApplicationStatus.SUBMITTED, Instant.parse("2026-05-10T09:00:00Z"));
        applicationsById.put(application.getId(), application);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", otherJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void nonEmployerRoleIsDenied() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void listApplicationsIncludesAllTerminalStatuses() throws Exception {
        Job job = buildJob(employerUser, JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);

        List.of(
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.UNDER_REVIEW,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN
        ).forEach(status -> {
            JobApplication app = buildApplication(job, seekerUser, status,
                    Instant.parse("2026-05-01T08:00:00Z"));
            applicationsById.put(app.getId(), app);
        });

        mockMvc.perform(get("/api/employer/jobs/{jobId}/applications", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    private String issueToken(UserAccount user) {
        return jwtTokenService.issueAccessToken(user).token();
    }

    @NonNull
    @SuppressWarnings("null")
    private static <T> T nonNullArgument(Class<T> type) {
        return ArgumentMatchers.notNull(type);
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
        account.setEnabled(true);
        account.getRoles().add(role);
        return account;
    }

    private Job buildJob(UserAccount employer, JobStatus status) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle("Engineer");
        job.setDescription("Build things");
        job.setStatus(status);
        return job;
    }

    private JobApplication buildApplication(Job job, UserAccount seeker,
            ApplicationStatus status, Instant submittedAt) {
        JobApplication application = new TestJobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setSeeker(seeker);
        application.setStatus(status);
        application.setSubmittedAt(submittedAt);
        return application;
    }

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }

    static final class TestJob extends Job {
    }

    static final class TestJobApplication extends JobApplication {
    }
}