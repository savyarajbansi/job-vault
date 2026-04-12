package com.project8.jobvault.auth;

import java.time.Instant;

public record AccessTokenResult(String token, Instant expiresAt) {
}
