package com.project8.jobvault.auth;

import java.util.Set;
import java.util.UUID;

public record AuthUserSummary(UUID id, Set<String> roles) {
}
