package com.project8.jobvault.auth;

import java.time.Instant;

public record AuthTokensResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        AuthUserSummary user) {
}
