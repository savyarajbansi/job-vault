package com.project8.jobvault.auth;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

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
