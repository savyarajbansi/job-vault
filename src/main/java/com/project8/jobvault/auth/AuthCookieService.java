package com.project8.jobvault.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthCookieService {
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final AuthCookieProperties properties;
    private final Clock clock;

    public AuthCookieService(AuthCookieProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        ResponseCookie cookie = buildCookie(
                properties.getRefreshTokenName(),
                token,
                true,
                properties.getPath(),
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void setCsrfTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                token,
                false,
                properties.getCsrfPath(),
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Expire any old CSRF cookie that was set under the narrower refresh-token
        // path (/api/auth). Active sessions that existed before the path fix would
        // keep sending the stale cookie; this one-shot expiry clears it on the
        // next login or refresh so those sessions self-heal without requiring a
        // manual logout.
        String refreshPath = properties.getPath();
        String csrfPath = properties.getCsrfPath();
        if (!refreshPath.equals(csrfPath)) {
            ResponseCookie legacy = buildCookie(
                    properties.getCsrfTokenName(),
                    "",
                    false,
                    refreshPath,
                    clock.instant()); // maxAge = 0 → immediate expiry
            response.addHeader(HttpHeaders.SET_COOKIE, legacy.toString());
        }
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie(
                properties.getRefreshTokenName(),
                "",
                true,
                properties.getPath(),
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCsrfTokenCookie(HttpServletResponse response) {
        // Clear the current (broad) path cookie.
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                "",
                false,
                properties.getCsrfPath(),
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Also clear any lingering cookie at the old narrow path, just in case
        // logout is called before a login/refresh had a chance to clean it up.
        String refreshPath = properties.getPath();
        String csrfPath = properties.getCsrfPath();
        if (!refreshPath.equals(csrfPath)) {
            ResponseCookie legacy = buildCookie(
                    properties.getCsrfTokenName(),
                    "",
                    false,
                    refreshPath,
                    clock.instant());
            response.addHeader(HttpHeaders.SET_COOKIE, legacy.toString());
        }
    }

    private ResponseCookie buildCookie(
            String name,
            String value,
            boolean httpOnly,
            String path,
            Instant expiresAt) {
        String cookieName = Objects.requireNonNull(requireConfigValue(name, "cookie name"), "cookie name");
        String sameSite = requireConfigValue(properties.getSameSite(), "jobvault.security.cookies.same-site");
        String cookiePath = requireConfigValue(path, "cookie path");
        Instant effectiveExpiresAt = requireExpiresAt(expiresAt);
        String cookieValue = value == null ? "" : value;
        Duration maxAge = Objects.requireNonNull(
                Duration.between(clock.instant(), effectiveExpiresAt),
                "maxAge");
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        return ResponseCookie.from(cookieName, cookieValue)
                .httpOnly(httpOnly)
                .secure(properties.isSecure())
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(Objects.requireNonNull(maxAge, "maxAge"))
                .build();
    }

    private String requireConfigValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing cookie configuration: " + name);
        }
        return value;
    }

    private Instant requireExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalStateException("Cookie expiresAt must not be null");
        }
        return expiresAt;
    }
}