package com.project8.jobvault.auth;

import java.util.Set;
import java.util.UUID;

public record AuthUserSummary(UUID id, String email, String displayName, Set<String> roles) {
}