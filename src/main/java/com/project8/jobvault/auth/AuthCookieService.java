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

    // The CSRF cookie must stay readable via document.cookie from every SPA
    // route (e.g. /seeker/matches), not just /api/auth/**. Cookie Path scopes
    // document.cookie visibility to the *current page's* path, not just which
    // outgoing requests carry the cookie -- scoping it the same as the refresh
    // token cookie meant the frontend could never read it outside auth pages,
    // so every refresh/logout call made while browsing elsewhere silently
    // dropped the X-CSRF-Token header and failed with ERR_AUTH_003.
    private static final String CSRF_COOKIE_PATH = "/";

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
                requireConfigValue(properties.getPath(), "jobvault.security.cookies.path"),
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void setCsrfTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                token,
                false,
                CSRF_COOKIE_PATH,
                expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie(
                properties.getRefreshTokenName(),
                "",
                true,
                requireConfigValue(properties.getPath(), "jobvault.security.cookies.path"),
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCsrfTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie(
                properties.getCsrfTokenName(),
                "",
                false,
                CSRF_COOKIE_PATH,
                clock.instant());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildCookie(String name, String value, boolean httpOnly, String path, Instant expiresAt) {
        String cookieName = Objects.requireNonNull(requireConfigValue(name, "cookie name"), "cookie name");
        String sameSite = requireConfigValue(properties.getSameSite(), "jobvault.security.cookies.same-site");
        String resolvedPath = requireConfigValue(path, "cookie path");
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
                .path(resolvedPath)
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