package com.project8.jobvault.admin;

import java.util.Set;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String email,
        String displayName,
        boolean enabled,
        Set<String> roles) {
}
