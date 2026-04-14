package com.project8.jobvault.admin;

import com.project8.jobvault.auth.JwtTokenService;
import com.project8.jobvault.auth.RefreshTokenRepository;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AdminUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    private final ConcurrentMap<UUID, UserAccount> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Role> rolesByName = new ConcurrentHashMap<>();

    private UserAccount adminUser;
    private UserAccount employerUser;
    private UserAccount seekerUser;

    @BeforeEach
    void setUp() {
        usersById.clear();
        rolesByName.clear();

        Role adminRole = buildRole("ADMIN");
        Role employerRole = buildRole("EMPLOYER");
        Role seekerRole = buildRole("JOB_SEEKER");

        rolesByName.put("ADMIN", adminRole);
        rolesByName.put("EMPLOYER", employerRole);
        rolesByName.put("JOB_SEEKER", seekerRole);

        adminUser = buildUser("admin@example.com", adminRole);
        employerUser = buildUser("employer@example.com", employerRole);
        seekerUser = buildUser("user@example.com", seekerRole);

        usersById.put(adminUser.getId(), adminUser);
        usersById.put(employerUser.getId(), employerUser);
        usersById.put(seekerUser.getId(), seekerUser);

        when(userAccountRepository.findAllByOrderByEmailAsc()).thenAnswer(invocation -> usersById.values().stream()
                .sorted(Comparator.comparing(UserAccount::getEmail))
                .toList());
        when(userAccountRepository.findWithRolesById(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return Optional.ofNullable(usersById.get(userId));
        });
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            usersById.put(user.getId(), user);
            return user;
        });
        when(roleRepository.findByName(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return Optional.ofNullable(rolesByName.get(name));
        });
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[1].email").value("employer@example.com"))
                .andExpect(jsonPath("$[2].email").value("user@example.com"));
    }

    @Test
    void adminCanDisableUser() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", seekerUser.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertFalse(usersById.get(seekerUser.getId()).isEnabled());
    }

    @Test
    void adminCanAssignRole() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/roles", seekerUser.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("EMPLOYER")));
    }

    @Test
    void adminCanRevokeRole() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}/roles/{role}", employerUser.getId(), "EMPLOYER")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", not(hasItem("EMPLOYER"))));
    }

    @Test
    void nonAdminIsDenied() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken(seekerUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_002"))
                .andExpect(jsonPath("$.details.reason").value("insufficient_role"));
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_001"));
    }

    private String issueToken(UserAccount user) {
        return jwtTokenService.issueAccessToken(user).token();
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
