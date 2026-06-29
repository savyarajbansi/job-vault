package com.project8.jobvault.auth;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {

    @Test
    void setRefreshTokenCookieThrowsWhenExpiresAtMissing() {
        AuthCookieProperties properties = buildProperties();
        AuthCookieService service = new AuthCookieService(properties, Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.setRefreshTokenCookie(response, "token", null));
        assertTrue(ex.getMessage().contains("expiresAt"));
    }

    @Test
    void setRefreshTokenCookieThrowsWhenCookieConfigMissing() {
        AuthCookieProperties properties = buildProperties();
        properties.setSameSite(null);
        AuthCookieService service = new AuthCookieService(properties, Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.setRefreshTokenCookie(response, "token", Instant.now()));
        assertTrue(ex.getMessage().contains("same-site"));
    }

    @Test
    void refreshTokenCookieUsesConfiguredPath() {
        AuthCookieProperties properties = buildProperties();
        AuthCookieService service = new AuthCookieService(properties, Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setRefreshTokenCookie(response, "refresh-value", Instant.now().plusSeconds(60));

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("Path=/api/auth"));
    }

    @Test
    void csrfCookieAlwaysUsesRootPathRegardlessOfConfiguredCookiePath() {
        // Regression test for the session-drop bug: the CSRF cookie must stay
        // readable via document.cookie from every SPA route, not just
        // /api/auth/**, or the frontend silently drops X-CSRF-Token on refresh.
        AuthCookieProperties properties = buildProperties();
        AuthCookieService service = new AuthCookieService(properties, Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setCsrfTokenCookie(response, "csrf-value", Instant.now().plusSeconds(60));

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("Path=/"));
        assertFalse(setCookieHeader.contains("Path=/api/auth"));
    }

    @Test
    void clearCsrfTokenCookieAlsoUsesRootPath() {
        AuthCookieProperties properties = buildProperties();
        AuthCookieService service = new AuthCookieService(properties, Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearCsrfTokenCookie(response);

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("Path=/"));
        assertFalse(setCookieHeader.contains("Path=/api/auth"));
    }

    private AuthCookieProperties buildProperties() {
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setRefreshTokenName("refresh_token");
        properties.setCsrfTokenName("csrf_token");
        properties.setSameSite("Lax");
        properties.setPath("/api/auth");
        properties.setSecure(true);
        return properties;
    }
}