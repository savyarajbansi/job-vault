package com.project8.jobvault.matching;

import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.skills.SkillRepository;
import com.project8.jobvault.skills.TrendingSkillRow;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
class MatchingEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private SkillRepository skillRepository;

    private UserAccount seekerUser;
    private UserAccount employerUser;
    private UserAccount otherEmployerUser;

    private ResumeMetadata seekerResume;
    private Job strongMatchJob;
    private Job weakMatchJob;

    @BeforeEach
    void setUp() {
        Role seekerRole = buildRole("JOB_SEEKER");
        Role employerRole = buildRole("EMPLOYER");
        seekerUser = buildUser("seeker@example.com", seekerRole);
        employerUser = buildUser("employer@example.com", employerRole);
        otherEmployerUser = buildUser("other-employer@example.com", employerRole);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (id.equals(seekerUser.getId())) {
                return Optional.of(seekerUser);
            }
            if (id.equals(employerUser.getId())) {
                return Optional.of(employerUser);
            }
            if (id.equals(otherEmployerUser.getId())) {
                return Optional.of(otherEmployerUser);
            }
            return Optional.empty();
        });

        seekerResume = new TestResumeMetadata();
        seekerResume.setId(UUID.randomUUID());
        seekerResume.setSeeker(seekerUser);
        seekerResume.setProcessingStatus(ResumeProcessingStatus.PARSED);
        seekerResume.setParsedText("Java Spring Boot SQL REST APIs");
        seekerResume.setInferredSkills("java,spring,sql");
        seekerResume.setParsedAt(Instant.parse("2026-05-09T12:00:00Z"));

        strongMatchJob = buildJob(
                employerUser,
                JobStatus.ACTIVE,
                "Backend Engineer",
                "Java Spring SQL microservices",
                Set.of(skill("java"), skill("spring"), skill("kubernetes")));
        weakMatchJob = buildJob(
                employerUser,
                JobStatus.ACTIVE,
                "Frontend Engineer",
                "React TypeScript UI performance",
                Set.of(skill("react"), skill("typescript")));

        when(resumeMetadataRepository.findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                seekerUser.getId(),
                ResumeProcessingStatus.PARSED)).thenReturn(Optional.of(seekerResume));
        when(resumeMetadataRepository.findAllByProcessingStatusOrderByParsedAtDesc(
                ResumeProcessingStatus.PARSED)).thenReturn(List.of(seekerResume));

        when(jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE))
                .thenReturn(List.of(strongMatchJob, weakMatchJob));
        when(jobRepository.findByIdAndStatus(nonNullArgument(), ArgumentMatchers.eq(JobStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (strongMatchJob.getId().equals(id)) {
                        return Optional.of(strongMatchJob);
                    }
                    if (weakMatchJob.getId().equals(id)) {
                        return Optional.of(weakMatchJob);
                    }
                    return Optional.empty();
                });
        when(jobRepository.findById(nonNullArgument()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (strongMatchJob.getId().equals(id)) {
                        return Optional.of(strongMatchJob);
                    }
                    if (weakMatchJob.getId().equals(id)) {
                        return Optional.of(weakMatchJob);
                    }
                    return Optional.empty();
                });

        when(skillRepository.findTrendingSkills())
                .thenReturn(List.of(
                        new TestTrendingSkillRow(UUID.randomUUID(), "java", new BigDecimal("10.5")),
                        new TestTrendingSkillRow(UUID.randomUUID(), "spring", new BigDecimal("7.0"))));
    }

    @Test
    void seekerMatchesReturnsRankedJobs() throws Exception {
        mockMvc.perform(get("/api/seeker/matches/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobId").value(strongMatchJob.getId().toString()))
                .andExpect(jsonPath("$.items[0].missingSkills[0]").value("kubernetes"))
                .andExpect(jsonPath("$.page.total").value(2));
    }

    @Test
    void seekerSkillGapReturnsMissingSkills() throws Exception {
        mockMvc.perform(get("/api/seeker/jobs/{jobId}/skill-gaps", strongMatchJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(strongMatchJob.getId().toString()))
                .andExpect(jsonPath("$.missingSkills[0]").value("kubernetes"));
    }

    @Test
    void employerCandidatesReturnsRankedResumes() throws Exception {
        mockMvc.perform(get("/api/employer/jobs/{jobId}/matches/candidates", strongMatchJob.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resumeId").value(seekerResume.getId().toString()))
                .andExpect(jsonPath("$.items[0].seekerId").value(seekerUser.getId().toString()));
    }

    @Test
    void trendingSkillsEndpointReturnsData() throws Exception {
        mockMvc.perform(get("/api/jobs/trending-skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skillName").value("java"))
                .andExpect(jsonPath("$[0].score").value(10.5));
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
        account.setEnabled(true);
        account.getRoles().add(role);
        return account;
    }

    private Job buildJob(UserAccount employer, JobStatus status, String title, String description, Set<Skill> requiredSkills) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setStatus(status);
        job.setTitle(title);
        job.setDescription(description);
        job.setRequiredSkills(new LinkedHashSet<>(requiredSkills));
        return job;
    }

    private Skill skill(String name) {
        Skill skill = new TestSkill();
        skill.setId(UUID.randomUUID());
        skill.setName(name);
        return skill;
    }

    private record TestTrendingSkillRow(UUID skillId, String skillName, BigDecimal score) implements TrendingSkillRow {
        @Override
        public UUID getSkillId() {
            return skillId;
        }

        @Override
        public String getSkillName() {
            return skillName;
        }

        @Override
        public BigDecimal getScore() {
            return score;
        }
    }

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }

    static final class TestResumeMetadata extends ResumeMetadata {
    }

    static final class TestJob extends Job {
    }

    static final class TestSkill extends Skill {
    }
}
