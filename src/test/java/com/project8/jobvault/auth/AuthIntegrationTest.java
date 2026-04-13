package com.project8.jobvault.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "jobvault.security.jwt.secret=unit-test-secret-should-be-at-least-32-bytes-long",
        "jobvault.security.jwt.refresh-hash-secret=unit-test-refresh-secret-should-be-at-least-32-bytes-long",
        "jobvault.security.jwt.issuer=jobvault-test",
        "jobvault.security.jwt.access-token-minutes=1",
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
@Import(AuthIntegrationTest.TestConfig.class)
class AuthIntegrationTest {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CSRF_COOKIE = "csrf_token";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    private final ConcurrentMap<UUID, RefreshToken> tokenStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> tokenHashIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UserAccount> userByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UserAccount> userById = new ConcurrentHashMap<>();

    private UserAccount seekerUser;
    private UserAccount employerUser;
    private UserAccount adminUser;

    @BeforeEach
    void setUp() {
        tokenStore.clear();
        tokenHashIndex.clear();
        userByEmail.clear();
        userById.clear();

        seekerUser = buildUser("user@example.com", "JOB_SEEKER");
        employerUser = buildUser("employer@example.com", "EMPLOYER");
        adminUser = buildUser("admin@example.com", "ADMIN");

        indexUser(seekerUser);
        indexUser(employerUser);
        indexUser(adminUser);

        when(userAccountRepository.findByEmail(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return Optional.ofNullable(userByEmail.get(email));
        });
        when(userAccountRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return Optional.ofNullable(userById.get(userId));
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            tokenStore.put(token.getId(), token);
            tokenHashIndex.put(token.getTokenHash(), token.getId());
            return token;
        });
        when(refreshTokenRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            UUID id = tokenHashIndex.get(hash);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(tokenStore.get(id));
        });
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalseOrderByExpiresAtAsc(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    return tokenStore.values().stream()
                            .filter(token -> token.getUser().getId().equals(userId))
                            .filter(token -> !token.isRevoked())
                            .sorted(Comparator.comparing(RefreshToken::getExpiresAt))
                            .toList();
                });
        when(refreshTokenRepository.findAllByUserId(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return tokenStore.values().stream()
                    .filter(token -> token.getUser().getId().equals(userId))
                    .toList();
        });
    }

    @Test
    void loginReturnsAccessTokenAndSetsCookies() throws Exception {
        MvcResult result = performLogin();
        String accessToken = extractAccessToken(result);
        assertNotNull(accessToken);

        String refreshToken = extractCookieValue(result, REFRESH_COOKIE);
        assertNotNull(refreshToken);
        assertTrue(hasCookieAttribute(result, REFRESH_COOKIE, "HttpOnly"));
        assertTrue(hasCookieAttribute(result, REFRESH_COOKIE, "Secure"));

        String csrfToken = extractCookieValue(result, CSRF_COOKIE);
        assertNotNull(csrfToken);
        assertTrue(hasCookieAttribute(result, CSRF_COOKIE, "SameSite=Lax"));
    }

    @Test
    void loginRejectsInvalidPayloadWithConsistentError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired token, or invalid authentication credentials/request payload."))
                .andExpect(jsonPath("$.details.reason").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.email").isNotEmpty())
                .andExpect(jsonPath("$.details.fields.password").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() throws Exception {
        MvcResult loginResult = performLogin();
        String refreshToken = extractCookieValue(loginResult, REFRESH_COOKIE);
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .cookie(TestCookies.cookie(REFRESH_COOKIE, refreshToken), TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String rotatedRefreshToken = extractCookieValue(refreshResult, REFRESH_COOKIE);
        assertNotEquals(refreshToken, rotatedRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(TestCookies.cookie(REFRESH_COOKIE, refreshToken), TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"));
    }

    @Test
    void refreshRejectsForgedTokenWithErrAuth003() throws Exception {
        MvcResult loginResult = performLogin();
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(TestCookies.cookie(REFRESH_COOKIE, "forged-refresh-token"),
                        TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"));
    }

    @Test
    void refreshRejectsWhenOriginAndRefererMissing() throws Exception {
        MvcResult loginResult = performLogin();
        String refreshToken = extractCookieValue(loginResult, REFRESH_COOKIE);
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(TestCookies.cookie(REFRESH_COOKIE, refreshToken), TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"))
                .andExpect(jsonPath("$.details.reason").value("missing_origin_or_referer"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));

        mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"));
    }

    @Test
    void meReturnsCurrentUserSummary() throws Exception {
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);

        mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seekerUser.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("JOB_SEEKER"));
    }

    @Test
    void roleProtectedEndpointsAllowMatchingRoleAndDenyMismatchedRole() throws Exception {
        String seekerAccessToken = extractAccessToken(performLogin("user@example.com"));
        String employerAccessToken = extractAccessToken(performLogin("employer@example.com"));
        String adminAccessToken = extractAccessToken(performLogin("admin@example.com"));

        mockMvc.perform(get("/api/admin/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + seekerAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));

        mockMvc.perform(get("/api/admin/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/employer/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + seekerAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));

        mockMvc.perform(get("/api/employer/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + employerAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshTokenFamilyAndClearsCookies() throws Exception {
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String refreshToken = extractCookieValue(loginResult, REFRESH_COOKIE);
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                .cookie(TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andReturn();

        assertEquals("", extractCookieValue(logoutResult, REFRESH_COOKIE));
        assertEquals("", extractCookieValue(logoutResult, CSRF_COOKIE));
        assertTrue(hasCookieAttribute(logoutResult, REFRESH_COOKIE, "Max-Age=0"));
        assertTrue(hasCookieAttribute(logoutResult, CSRF_COOKIE, "Max-Age=0"));

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(TestCookies.cookie(REFRESH_COOKIE, refreshToken), TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"));
    }

    @Test
    void logoutRejectsMissingCsrfWithErrAuth003() throws Exception {
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("Origin", FRONTEND_ORIGIN)
                .header("Referer", FRONTEND_ORIGIN + "/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"));
    }

    @Test
    void logoutRejectsWhenOriginAndRefererMissingWithErrAuth003() throws Exception {
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String csrfToken = extractCookieValue(loginResult, CSRF_COOKIE);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(TestCookies.cookie(CSRF_COOKIE, csrfToken))
                .header("X-CSRF-Token", csrfToken)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_003"));
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"));

        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        MutableClock mutableClock = (MutableClock) clock;
        mutableClock.advance(Duration.ofMinutes(2));

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"));
    }

    @Test
    void corsPreflightAllowsLocalFrontendOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/me")
                .header("Origin", FRONTEND_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,X-CSRF-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private MvcResult performLogin() throws Exception {
        return performLogin("user@example.com");
    }

    private MvcResult performLogin(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isNotEmpty())
                .andReturn();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return node.path("accessToken").asText(null);
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(cookieName + "=")) {
                String[] parts = header.split(";", 2);
                String[] tokenParts = parts[0].split("=", 2);
                return tokenParts.length > 1 ? tokenParts[1] : null;
            }
        }
        return null;
    }

    private boolean hasCookieAttribute(MvcResult result, String cookieName, String attribute) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(cookieName + "=") && header.contains(attribute)) {
                return true;
            }
        }
        return false;
    }

    private void indexUser(UserAccount account) {
        userByEmail.put(account.getEmail(), account);
        userById.put(account.getId(), account);
    }

    private UserAccount buildUser(String email, String roleName) {
        Role role = new TestRole();
        role.setName(roleName);

        UserAccount account = new TestUserAccount();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode("password"));
        account.getRoles().add(role);
        account.setEnabled(true);
        return account;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-04-11T00:00:00Z"), ZoneOffset.UTC);
        }

        @RestController
        @RequestMapping("/api/test")
        static class TestEndpoints {
            @GetMapping("/protected")
            Map<String, String> protectedEndpoint() {
                return Map.of("status", "ok");
            }
        }

        @RestController
        @RequestMapping("/api")
        static class RoleProtectedTestEndpoints {
            @GetMapping("/admin/test")
            Map<String, String> adminEndpoint() {
                return Map.of("status", "admin-ok");
            }

            @GetMapping("/employer/test")
            Map<String, String> employerEndpoint() {
                return Map.of("status", "employer-ok");
            }
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.instant = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant.get(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }
    }

    static class TestCookies {
        static jakarta.servlet.http.Cookie cookie(String name, String value) {
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, value);
            cookie.setPath("/");
            return cookie;
        }
    }

    static final class TestRole extends Role {
    }

    static final class TestUserAccount extends UserAccount {
    }
}
