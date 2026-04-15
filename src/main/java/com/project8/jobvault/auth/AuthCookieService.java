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
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void setCsrfTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                token,
                false,
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie(
                properties.getRefreshTokenName(),
                "",
                true,
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCsrfTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                "",
                false,
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildCookie(String name, String value, boolean httpOnly, Instant expiresAt) {
        String cookieName = Objects.requireNonNull(requireConfigValue(name, "cookie name"), "cookie name");
        String sameSite = requireConfigValue(properties.getSameSite(), "jobvault.security.cookies.same-site");
        String path = requireConfigValue(properties.getPath(), "jobvault.security.cookies.path");
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
                .path(path)
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
