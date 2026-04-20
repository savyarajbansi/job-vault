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
import com.project8.jobvault.testing.DeterministicConcurrencyHarness;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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
class ApplicationTransitionIntegrationTest {

    // Reference for t2 coverage: idempotent_409_transition_table application
    // entries.

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

    private final ConcurrentMap<UUID, UserAccount> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Job> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JobApplication> applicationsById = new ConcurrentHashMap<>();

    private UserAccount employerUser;
    private UserAccount otherEmployerUser;
    private UserAccount seekerUser;
    private UserAccount otherSeekerUser;

    @BeforeEach
    void setUp() {
        usersById.clear();
        jobsById.clear();
        applicationsById.clear();

        Role employerRole = buildRole("EMPLOYER");
        Role seekerRole = buildRole("JOB_SEEKER");

        employerUser = buildUser("employer@example.com", employerRole);
        otherEmployerUser = buildUser("other-employer@example.com", employerRole);
        seekerUser = buildUser("seeker@example.com", seekerRole);
        otherSeekerUser = buildUser("other-seeker@example.com", seekerRole);

        usersById.put(employerUser.getId(), employerUser);
        usersById.put(otherEmployerUser.getId(), otherEmployerUser);
        usersById.put(seekerUser.getId(), seekerUser);
        usersById.put(otherSeekerUser.getId(), otherSeekerUser);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return Optional.ofNullable(usersById.get(userId));
        });

        when(jobRepository.findByIdAndStatus(nonNullArgument(), nonNullArgument())).thenAnswer(invocation -> {
            UUID jobId = invocation.getArgument(0);
            JobStatus status = invocation.getArgument(1);
            Job job = jobsById.get(jobId);
            if (job == null || job.getStatus() != status) {
                return Optional.empty();
            }
            return Optional.of(job);
        });

        when(jobApplicationRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID applicationId = invocation.getArgument(0);
            return Optional.ofNullable(applicationsById.get(applicationId));
        });

        when(jobApplicationRepository.findByJobIdAndSeekerId(nonNullArgument(), nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UUID seekerId = invocation.getArgument(1);
                    return applicationsById.values().stream()
                            .filter(app -> app.getJob().getId().equals(jobId))
                            .filter(app -> app.getSeeker().getId().equals(seekerId))
                            .findFirst();
                });

        when(jobApplicationRepository.findByIdAndJobEmployerId(nonNullArgument(), nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID appId = invocation.getArgument(0);
                    UUID employerId = invocation.getArgument(1);
                    JobApplication application = applicationsById.get(appId);
                    if (application == null || application.getJob().getEmployer() == null) {
                        return Optional.empty();
                    }
                    if (!employerId.equals(application.getJob().getEmployer().getId())) {
                        return Optional.empty();
                    }
                    return Optional.of(application);
                });

        when(jobApplicationRepository.findByIdAndSeekerId(nonNullArgument(), nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID appId = invocation.getArgument(0);
                    UUID seekerId = invocation.getArgument(1);
                    JobApplication application = applicationsById.get(appId);
                    if (application == null || application.getSeeker() == null) {
                        return Optional.empty();
                    }
                    if (!seekerId.equals(application.getSeeker().getId())) {
                        return Optional.empty();
                    }
                    return Optional.of(application);
                });

        when(jobApplicationRepository.transitionDraftToSubmitted(nonNullArgument(), nonNullArgument(),
                nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID jobId = invocation.getArgument(0);
                    UUID seekerId = invocation.getArgument(1);
                    Instant submittedAt = invocation.getArgument(2);
                    Optional<JobApplication> application = applicationsById.values().stream()
                            .filter(app -> app.getJob().getId().equals(jobId))
                            .filter(app -> app.getSeeker().getId().equals(seekerId))
                            .findFirst();
                    if (application.isEmpty() || application.get().getStatus() != ApplicationStatus.DRAFT) {
                        return 0;
                    }
                    JobApplication existing = application.get();
                    existing.setStatus(ApplicationStatus.SUBMITTED);
                    existing.setSubmittedAt(submittedAt);
                    applicationsById.put(existing.getId(), existing);
                    return 1;
                });

        when(jobApplicationRepository.transitionForSeeker(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                ArgumentMatchers.nullable(Instant.class),
                ArgumentMatchers.nullable(Instant.class))).thenAnswer(invocation -> {
                    UUID applicationId = invocation.getArgument(0);
                    UUID seekerId = invocation.getArgument(1);
                    ApplicationStatus expected = invocation.getArgument(2);
                    ApplicationStatus newStatus = invocation.getArgument(3);
                    Instant reviewedAt = invocation.getArgument(4);
                    Instant decidedAt = invocation.getArgument(5);
                    JobApplication application = applicationsById.get(applicationId);
                    if (application == null || application.getSeeker() == null) {
                        return 0;
                    }
                    if (!seekerId.equals(application.getSeeker().getId())) {
                        return 0;
                    }
                    if (application.getStatus() != expected) {
                        return 0;
                    }
                    application.setStatus(newStatus);
                    if (reviewedAt != null) {
                        application.setReviewedAt(reviewedAt);
                    }
                    if (decidedAt != null) {
                        application.setDecidedAt(decidedAt);
                    }
                    applicationsById.put(applicationId, application);
                    return 1;
                });

        when(jobApplicationRepository.transitionForEmployer(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                ArgumentMatchers.nullable(Instant.class),
                ArgumentMatchers.nullable(Instant.class))).thenAnswer(invocation -> {
                    UUID applicationId = invocation.getArgument(0);
                    UUID employerId = invocation.getArgument(1);
                    ApplicationStatus expected = invocation.getArgument(2);
                    ApplicationStatus newStatus = invocation.getArgument(3);
                    Instant reviewedAt = invocation.getArgument(4);
                    Instant decidedAt = invocation.getArgument(5);
                    JobApplication application = applicationsById.get(applicationId);
                    if (application == null || application.getJob().getEmployer() == null) {
                        return 0;
                    }
                    if (!employerId.equals(application.getJob().getEmployer().getId())) {
                        return 0;
                    }
                    if (application.getStatus() != expected) {
                        return 0;
                    }
                    application.setStatus(newStatus);
                    if (reviewedAt != null) {
                        application.setReviewedAt(reviewedAt);
                    }
                    if (decidedAt != null) {
                        application.setDecidedAt(decidedAt);
                    }
                    applicationsById.put(applicationId, application);
                    return 1;
                });

        when(jobApplicationRepository.save(nonNullArgument())).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            if (application.getId() == null) {
                application.setId(UUID.randomUUID());
            }
            applicationsById.put(application.getId(), application);
            return application;
        });
    }

    @Test
    void applyCreatesSubmittedWhenNoDraftExists() throws Exception {
        Job job = buildActiveJob();

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/apply", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void draftThenApplyTransitionsDraftToSubmitted() throws Exception {
        Job job = buildActiveJob();

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/draft", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/apply", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void draftReturnsConflictWhenAnyApplicationAlreadyExists() throws Exception {
        Job job = buildActiveJob();
        JobApplication existing = buildApplication(job, seekerUser, ApplicationStatus.WITHDRAWN);
        applicationsById.put(existing.getId(), existing);

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/draft", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void applyReturnsUnauthorizedWhenUnauthenticated() throws Exception {
        Job job = buildActiveJob();

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/apply", job.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void employerStatusUpdateReturnsUnauthorizedWhenUnauthenticated() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void applyAfterWithdrawReturnsConflictWithoutMutation() throws Exception {
        Job job = buildActiveJob();
        JobApplication withdrawn = buildApplication(job, seekerUser, ApplicationStatus.WITHDRAWN);
        applicationsById.put(withdrawn.getId(), withdrawn);

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/apply", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());

        assertEquals(ApplicationStatus.WITHDRAWN, applicationsById.get(withdrawn.getId()).getStatus());
    }

    @Test
    void seekerCanWithdrawFromSubmittedAndSecondWithdrawIsConflict() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/seeker/applications/{applicationId}/withdraw", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(patch("/api/seeker/applications/{applicationId}/withdraw", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawReturnsNotFoundWhenApplicationDoesNotExist() throws Exception {
        mockMvc.perform(patch("/api/seeker/applications/{applicationId}/withdraw", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftReturnsNotFoundWhenJobMissing() throws Exception {
        mockMvc.perform(post("/api/seeker/jobs/{jobId}/draft", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void employerTransitionPrecedenceIs403Then404Then409() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"));

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(otherEmployerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict());

        assertEquals(ApplicationStatus.SUBMITTED, applicationsById.get(submitted.getId()).getStatus());
    }

    @Test
    void employerCanTransitionSubmittedToUnderReviewToAccepted() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void employerCanTransitionUnderReviewToRejectedAndInvalidFollowUpsConflictWithoutMutation() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict());

        assertEquals(ApplicationStatus.REJECTED, applicationsById.get(submitted.getId()).getStatus());
    }

    @Test
    void seekerCanTransitionUnderReviewToWithdrawnAndInvalidFollowUpsConflictWithoutMutation() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(patch("/api/seeker/applications/{applicationId}/withdraw", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(patch("/api/seeker/applications/{applicationId}/withdraw", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", submitted.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isConflict());

        assertEquals(ApplicationStatus.WITHDRAWN, applicationsById.get(submitted.getId()).getStatus());
    }

    @Test
    void uniqueConstraintViolationOnDraftMapsToConflict() throws Exception {
        Job job = buildActiveJob();
        doThrow(new DataIntegrityViolationException("uq_applications_job_seeker"))
                .when(jobApplicationRepository)
                .save(ArgumentMatchers.any(JobApplication.class));

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/draft", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void uniqueConstraintViolationOnApplyMapsToConflict() throws Exception {
        Job job = buildActiveJob();
        doThrow(new DataIntegrityViolationException("uq_applications_job_seeker"))
                .when(jobApplicationRepository)
                .save(ArgumentMatchers.any(JobApplication.class));

        mockMvc.perform(post("/api/seeker/jobs/{jobId}/apply", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentEmployerTransitionRaceHasSingleWinnerAndSingleConflict() throws Exception {
        Job job = buildActiveJob();
        JobApplication submitted = buildApplication(job, seekerUser, ApplicationStatus.SUBMITTED);
        applicationsById.put(submitted.getId(), submitted);

        CountDownLatch updateStart = new CountDownLatch(2);
        AtomicInteger updateWinner = new AtomicInteger(0);
        when(jobApplicationRepository.transitionForEmployer(
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                nonNullArgument(),
                ArgumentMatchers.nullable(Instant.class),
                ArgumentMatchers.nullable(Instant.class))).thenAnswer(invocation -> {
                    updateStart.countDown();
                    boolean started = updateStart.await(5, TimeUnit.SECONDS);
                    if (!started) {
                        return 0;
                    }
                    if (updateWinner.compareAndSet(0, 1)) {
                        UUID applicationId = invocation.getArgument(0);
                        Instant reviewedAt = invocation.getArgument(4);
                        JobApplication application = applicationsById.get(applicationId);
                        application.setStatus(ApplicationStatus.UNDER_REVIEW);
                        application.setReviewedAt(reviewedAt);
                        applicationsById.put(applicationId, application);
                        return 1;
                    }
                    return 0;
                });

        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "application-under-review-race",
                "contender-a",
                () -> patchEmployerStatus(submitted.getId(), employerUser, "UNDER_REVIEW"),
                "contender-b",
                () -> patchEmployerStatus(submitted.getId(), employerUser, "UNDER_REVIEW"));

        DeterministicConcurrencyHarness.assertSingleWinnerAndConflict(
                "application-under-review-race", result, 200);
        assertEquals(ApplicationStatus.UNDER_REVIEW, applicationsById.get(submitted.getId()).getStatus());
    }

    @Test
    void concurrentDraftApplyCreateRaceHasSingleWinnerAndSingleConflictWithoutMutation() throws Exception {
        Job job = buildActiveJob();
        CountDownLatch createStart = new CountDownLatch(2);
        AtomicInteger saveCalls = new AtomicInteger(0);
        ConcurrentLinkedQueue<ApplicationStatus> savedStatuses = new ConcurrentLinkedQueue<>();

        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            createStart.countDown();
            boolean started = createStart.await(5, TimeUnit.SECONDS);
            if (!started) {
                throw new IllegalStateException("create race setup timed out");
            }
            JobApplication application = invocation.getArgument(0);
            int saveAttempt = saveCalls.incrementAndGet();
            if (saveAttempt > 1) {
                throw new DataIntegrityViolationException("uq_applications_job_seeker");
            }
            if (application.getId() == null) {
                application.setId(UUID.randomUUID());
            }
            applicationsById.put(application.getId(), application);
            savedStatuses.add(application.getStatus());
            return application;
        });

        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "application-draft-apply-create-race",
                "draft",
                () -> postSeekerJobAction(job.getId(), seekerUser, "draft"),
                "apply",
                () -> postSeekerJobAction(job.getId(), seekerUser, "apply"));

        DeterministicConcurrencyHarness.assertSingleWinnerAndConflict(
                "application-draft-apply-create-race", result, 201);
        assertEquals(1, applicationsById.size());
        assertEquals(1, savedStatuses.size());
        ApplicationStatus savedStatus = savedStatuses.peek();
        assertTrue(savedStatus == ApplicationStatus.DRAFT || savedStatus == ApplicationStatus.SUBMITTED);
    }

    private DeterministicConcurrencyHarness.ContentionResponse patchEmployerStatus(
            UUID applicationId,
            UserAccount user,
            String statusValue) throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/employer/applications/{applicationId}/status", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + statusValue + "\"}"))
                .andReturn();
        return DeterministicConcurrencyHarness.ContentionResponse.of(
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private DeterministicConcurrencyHarness.ContentionResponse postSeekerJobAction(
            UUID jobId,
            UserAccount user,
            String action) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/seeker/jobs/{jobId}/" + action, jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(user)))
                .andReturn();
        return DeterministicConcurrencyHarness.ContentionResponse.of(
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private String issueToken(UserAccount user) {
        return jwtTokenService.issueAccessToken(user).token();
    }

    private Job buildActiveJob() {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employerUser);
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs");
        job.setStatus(JobStatus.ACTIVE);
        jobsById.put(job.getId(), job);
        return job;
    }

    private JobApplication buildApplication(Job job, UserAccount seeker, ApplicationStatus status) {
        JobApplication application = new TestJobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setSeeker(seeker);
        application.setStatus(status);
        if (status == ApplicationStatus.SUBMITTED
                || status == ApplicationStatus.UNDER_REVIEW
                || status == ApplicationStatus.REJECTED
                || status == ApplicationStatus.ACCEPTED
                || status == ApplicationStatus.WITHDRAWN) {
            application.setSubmittedAt(Instant.parse("2026-04-20T06:30:00Z"));
        }
        return application;
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
        account.setEnabled(true);
        account.getRoles().add(role);
        return account;
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
