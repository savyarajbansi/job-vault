package com.project8.jobvault.auth;

import java.time.Instant;

public record RefreshTokenResult(String token, Instant expiresAt) {
}
