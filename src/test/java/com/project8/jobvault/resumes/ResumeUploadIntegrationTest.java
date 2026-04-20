package com.project8.jobvault.resumes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project8.jobvault.applications.JobApplicationRepository;
import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.jobs.EmployerJobController;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.PublicJobController;
import com.project8.jobvault.notifications.NotificationRepository;
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
import org.mockito.ArgumentMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class ResumeUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ResumeMetadataRepository resumeMetadataRepository;

    @MockitoBean
    private ResumeStorageService resumeStorageService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private EmployerJobController employerJobController;

    @MockitoBean
    private PublicJobController publicJobController;

    private final ConcurrentMap<UUID, ResumeMetadata> resumesById = new ConcurrentHashMap<>();

    private UserAccount seekerUser;

    @BeforeEach
    void setUp() throws Exception {
        resumesById.clear();

        Role seekerRole = buildRole("JOB_SEEKER");
        seekerUser = buildUser("user@example.com", seekerRole);

        when(userAccountRepository.findById(nonNullArgument())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            if (seekerUser.getId().equals(userId)) {
                return Optional.of(seekerUser);
            }
            return Optional.empty();
        });

        when(resumeMetadataRepository.save(nonNullArgument())).thenAnswer(invocation -> {
            ResumeMetadata metadata = invocation.getArgument(0);
            if (metadata.getId() == null) {
                metadata.setId(UUID.randomUUID());
            }
            resumesById.put(metadata.getId(), metadata);
            return metadata;
        });

        when(resumeStorageService.store(
                nonNullArgument(),
                nonNullArgument())).thenAnswer(invocation -> {
                    UUID resumeId = invocation.getArgument(0);
                    return "storage/resumes/" + resumeId + ".pdf";
                });
    }

    @Test
    void seekerCanUploadPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "resume content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").exists())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID resumeId = UUID.fromString(node.path("resumeId").asText());
        ResumeMetadata metadata = resumesById.get(resumeId);
        org.junit.jupiter.api.Assertions.assertNotNull(metadata);
    }

    @Test
    void uploadRejectsNonPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "resume content".getBytes());

        mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ERR_UPLOAD_001"));
    }

    @Test
    void uploadAcceptsPdfWithMissingContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                null,
                "resume content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID resumeId = UUID.fromString(node.path("resumeId").asText());
        ResumeMetadata metadata = resumesById.get(resumeId);
        org.junit.jupiter.api.Assertions.assertNotNull(metadata);
        assertEquals("application/pdf", metadata.getContentType());
    }

    @Test
    void uploadRejectsMissingContentTypeWithoutPdfExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                null,
                "resume content".getBytes());

        mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ERR_UPLOAD_001"));
    }

    @Test
    void uploadRejectsOversizedPdf() throws Exception {
        int size = 11 * 1024 * 1024;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                new byte[size]);

        mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ERR_UPLOAD_002"))
                .andExpect(jsonPath("$.details.reason").value("file_too_large"));
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "resume content".getBytes());

        mockMvc.perform(multipart("/api/seeker/resumes/upload")
                .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
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
