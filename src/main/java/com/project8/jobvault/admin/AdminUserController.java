package com.project8.jobvault.admin;

import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;

    public AdminUserController(UserAccountRepository userAccountRepository, RoleRepository roleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<AdminUserSummary> listUsers() {
        return userAccountRepository.findAllByOrderByEmailAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @PatchMapping("/{userId}/enabled")
    public ResponseEntity<AdminUserSummary> updateEnabled(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserStatusRequest request) {
        UserAccount user = userAccountRepository.findWithRolesById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        user.setEnabled(Boolean.TRUE.equals(request.enabled()));
        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(toSummary(saved));
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<AdminUserSummary> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminRoleRequest request) {
        UserAccount user = userAccountRepository.findWithRolesById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        Role role = roleRepository.findByName(request.role()).orElse(null);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        if (!hasRole(user, role.getName())) {
            user.getRoles().add(role);
        }
        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(toSummary(saved));
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    public ResponseEntity<AdminUserSummary> revokeRole(
            @PathVariable UUID userId,
            @PathVariable String roleName) {
        UserAccount user = userAccountRepository.findWithRolesById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        Role role = roleRepository.findByName(roleName).orElse(null);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        user.getRoles().removeIf(existing -> roleName.equals(existing.getName()));
        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(toSummary(saved));
    }

    private boolean hasRole(UserAccount user, String roleName) {
        if (user.getRoles() == null || roleName == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(roleName::equals);
    }

    private AdminUserSummary toSummary(UserAccount user) {
        return new AdminUserSummary(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                extractRoles(user));
    }

    private Set<String> extractRoles(UserAccount user) {
        if (user.getRoles() == null) {
            return Set.of();
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
