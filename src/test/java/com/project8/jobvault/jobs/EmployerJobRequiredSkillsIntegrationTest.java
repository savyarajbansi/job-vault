package com.project8.jobvault.jobs;

import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.skills.SkillRepository;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.Locale;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class EmployerJobRequiredSkillsIntegrationTest {

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

    @MockitoBean
    private JobRequiredSkillSyncService jobRequiredSkillSyncService;

    @MockitoBean
    private SkillRepository skillRepository;

    private final ConcurrentMap<UUID, Job> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Skill> skillsByName = new ConcurrentHashMap<>();

    private UserAccount employerUser;

    @BeforeEach
    void setUp() {
        jobsById.clear();
        skillsByName.clear();

        Role employerRole = buildRole("EMPLOYER");
        employerUser = buildUser("employer@example.com", employerRole);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return employerUser.getId().equals(userId) ? Optional.of(employerUser) : Optional.empty();
        });

        when(jobRepository.save(nonNullArgument())).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            jobsById.put(job.getId(), job);
            return job;
        });
        when(jobRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID jobId = invocation.getArgument(0);
            return Optional.ofNullable(jobsById.get(jobId));
        });

        // Stands in for HeuristicJobRequiredSkillSyncService: simulates adding one
        // auto-detected skill to whatever job was just saved.
        doAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            Job stored = jobsById.get(job.getId());
            if (stored != null) {
                stored.getRequiredSkills().add(skill("java"));
            }
            return null;
        }).when(jobRequiredSkillSyncService).syncRequiredSkills(nonNullArgument());

        when(skillRepository.findByNameIgnoreCase(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return Optional.ofNullable(skillsByName.get(name.toLowerCase(Locale.ROOT)));
        });
        when(skillRepository.save(nonNullArgument())).thenAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            if (skill.getId() == null) {
                skill.setId(UUID.randomUUID());
            }
            skillsByName.put(skill.getName().toLowerCase(Locale.ROOT), skill);
            return skill;
        });
    }

    @Test
    void createPopulatesAutoDetectedSkillsImmediately() throws Exception {
        mockMvc.perform(post("/api/employer/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""
                        {
                          "title":"Backend Engineer",
                          "description":"We use Java and Spring heavily."
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredSkills", hasItem("java")));
    }

    @Test
    void employerCanManuallyAddARequiredSkill() throws Exception {
        Job job = buildJob(employerUser);
        jobsById.put(job.getId(), job);

        mockMvc.perform(post("/api/employer/jobs/{jobId}/skills", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Kubernetes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills", hasItem("kubernetes")));
    }

    @Test
    void employerCanRemoveARequiredSkill() throws Exception {
        Job job = buildJob(employerUser);
        job.getRequiredSkills().add(skill("java"));
        jobsById.put(job.getId(), job);

        mockMvc.perform(delete("/api/employer/jobs/{jobId}/skills/{skillName}", job.getId(), "java")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills", not(hasItem("java"))));
    }

    @Test
    void cannotModifySkillsOnDisabledJob() throws Exception {
        Job job = buildJob(employerUser);
        job.setStatus(JobStatus.DISABLED);
        jobsById.put(job.getId(), job);

        mockMvc.perform(post("/api/employer/jobs/{jobId}/skills", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(employerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Go\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addSkillReturnsNotFoundForJobOwnedByAnotherEmployer() throws Exception {
        Role otherRole = buildRole("EMPLOYER");
        UserAccount otherEmployer = buildUser("other@example.com", otherRole);
        when(userAccountRepository.findById(otherEmployer.getId())).thenReturn(Optional.of(otherEmployer));

        Job job = buildJob(employerUser);
        jobsById.put(job.getId(), job);

        mockMvc.perform(post("/api/employer/jobs/{jobId}/skills", job.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(otherEmployer))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Go\"}"))
                .andExpect(status().isNotFound());
    }

    private Skill skill(String name) {
        return skillsByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
            Skill created = new TestSkill();
            created.setId(UUID.randomUUID());
            created.setName(name);
            return created;
        });
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

    private Job buildJob(UserAccount employer) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle("Role");
        job.setDescription("Description");
        job.setStatus(JobStatus.DRAFT);
        job.setRequiredSkills(new java.util.LinkedHashSet<>());
        return job;
    }

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }

    static final class TestJob extends Job {
    }

    static final class TestSkill extends Skill {
    }
}
