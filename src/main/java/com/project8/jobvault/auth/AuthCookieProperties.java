package com.project8.jobvault.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jobvault.security.cookies")
public class AuthCookieProperties {
    @NotBlank
    private String refreshTokenName;

    @NotBlank
    private String csrfTokenName;

    @NotBlank
    private String sameSite;

    /**
     * Path for the HttpOnly refresh-token cookie. Should be restricted to the
     * auth endpoint (e.g. {@code /api/auth}) so the browser only sends it there.
     */
    @NotBlank
    private String path;

    /**
     * Path for the CSRF double-submit cookie. Must be {@code /} (or at least
     * broad enough to cover every API endpoint) so the browser includes it on
     * all requests and JS can read it to place in the {@code X-CSRF-Token}
     * header. Defaults to {@code /} when not set in config.
     */
    private String csrfPath = "/";

    private boolean secure;

    public String getRefreshTokenName() {
        return refreshTokenName;
    }

    public void setRefreshTokenName(String refreshTokenName) {
        this.refreshTokenName = refreshTokenName;
    }

    public String getCsrfTokenName() {
        return csrfTokenName;
    }

    public void setCsrfTokenName(String csrfTokenName) {
        this.csrfTokenName = csrfTokenName;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCsrfPath() {
        return csrfPath == null || csrfPath.isBlank() ? "/" : csrfPath;
    }

    public void setCsrfPath(String csrfPath) {
        this.csrfPath = csrfPath;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}