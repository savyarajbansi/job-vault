package com.project8.jobvault.jobs;

import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.List;
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
class EmployerJobListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

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
        otherEmployer = buildUser("other@example.com", employerRole);
        seekerUser = buildUser("seeker@example.com", seekerRole);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            if (employerUser.getId().equals(userId)) return Optional.of(employerUser);
            if (otherEmployer.getId().equals(userId)) return Optional.of(otherEmployer);
            if (seekerUser.getId().equals(userId)) return Optional.of(seekerUser);
            return Optional.empty();
        });

        when(jobRepository.findAllByEmployerIdOrderByCreatedAtDesc(nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID employerId = invocation.getArgument(0);
                    return jobsById.values().stream()
                            .filter(job -> job.getEmployer() != null
                                    && employerId.equals(job.getEmployer().getId()))
                            .sorted((a, b) -> {
                                // Stable ordering by ID when createdAt is null in tests
                                if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                                }
                                return a.getId().compareTo(b.getId());
                            })
                            .toList();
                });
    }

    @Test
    void employerCanListOwnJobs() throws Exception {
        Job draft = buildJob(employerUser, JobStatus.DRAFT, "Backend Engineer");
        Job active = buildJob(employerUser, JobStatus.ACTIVE, "Frontend Engineer");
        jobsById.put(draft.getId(), draft);
        jobsById.put(active.getId(), active);

        mockMvc.perform(get("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listReturnsOnlyOwnEmployersJobs() throws Exception {
        Job ownJob = buildJob(employerUser, JobStatus.ACTIVE, "My Job");
        Job otherJob = buildJob(otherEmployer, JobStatus.ACTIVE, "Other Job");
        jobsById.put(ownJob.getId(), ownJob);
        jobsById.put(otherJob.getId(), otherJob);

        mockMvc.perform(get("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownJob.getId().toString()));
    }

    @Test
    void listReturnsEmptyArrayWhenNoJobsExist() throws Exception {
        mockMvc.perform(get("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listResponseIncludesExpectedFields() throws Exception {
        Job job = buildJob(employerUser, JobStatus.DRAFT, "Platform Engineer");
        job.setLocation("Kathmandu");
        job.setRemoteEligible(true);
        job.setMinExperienceYears(3);
        jobsById.put(job.getId(), job);

        mockMvc.perform(get("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(job.getId().toString()))
                .andExpect(jsonPath("$[0].title").value("Platform Engineer"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].location").value("Kathmandu"))
                .andExpect(jsonPath("$[0].remoteEligible").value(true))
                .andExpect(jsonPath("$[0].minExperienceYears").value(3));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/employer/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    @Test
    void nonEmployerRoleIsDenied() throws Exception {
        mockMvc.perform(get("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
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

    private Job buildJob(UserAccount employer, JobStatus status, String title) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle(title);
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