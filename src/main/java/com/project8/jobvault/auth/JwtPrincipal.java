package com.project8.jobvault.auth;

import java.util.List;
import java.util.UUID;

public record JwtPrincipal(UUID userId, List<String> roles) {
}
