package com.project8.jobvault.users;

import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.notifications.NotificationRepository;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeStorageService;
import java.util.Optional;
import java.util.UUID;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class SeekerProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private JobRepository jobRepository;

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

    private UserAccount seekerUser;

    @BeforeEach
    void setUp() {
        Role seekerRole = buildRole("JOB_SEEKER");
        seekerUser = buildUser("seeker@example.com", seekerRole);
        seekerUser.setPreferredSectors("SOFTWARE,IT");
        seekerUser.setPreferredLocation("Kathmandu");
        seekerUser.setWorkMode(com.project8.jobvault.matching.WorkMode.REMOTE);
        seekerUser.setYearsExperience(4);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            if (seekerUser.getId().equals(userId)) {
                return Optional.of(seekerUser);
            }
            return Optional.empty();
        });

        when(userAccountRepository.save(nonNullArgument())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getProfileReturnsExplicitFields() throws Exception {
        mockMvc.perform(get("/api/seeker/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSectors[0]").value("SOFTWARE"))
                .andExpect(jsonPath("$.preferredSectors[1]").value("IT"))
                .andExpect(jsonPath("$.preferredLocation").value("Kathmandu"))
                .andExpect(jsonPath("$.workMode").value("REMOTE"))
                .andExpect(jsonPath("$.yearsExperience").value(4));
    }

    @Test
    void patchProfileUpdatesAndNormalizesExplicitFields() throws Exception {
        mockMvc.perform(patch("/api/seeker/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""
                        {
                          "preferredSectors":["IT"],
                          "preferredLocation":"  Lalitpur  ",
                          "workMode":"HYBRID",
                          "yearsExperience":7
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSectors[0]").value("IT"))
                .andExpect(jsonPath("$.preferredLocation").value("Lalitpur"))
                .andExpect(jsonPath("$.workMode").value("HYBRID"))
                .andExpect(jsonPath("$.yearsExperience").value(7));
    }

    @Test
    void patchProfileRejectsOutOfRangeYearsExperience() throws Exception {
        mockMvc.perform(patch("/api/seeker/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""
                        {
                          "preferredSectors":["IT"],
                          "yearsExperience":61
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION_001"))
                .andExpect(jsonPath("$.details.reason").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.yearsExperience").exists());
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

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }
}
