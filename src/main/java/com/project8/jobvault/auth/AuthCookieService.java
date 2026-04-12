package com.project8.jobvault.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.isSecure())
                .path(properties.getPath())
                .sameSite(properties.getSameSite())
                .maxAge(maxAge)
                .build();
    }
}
