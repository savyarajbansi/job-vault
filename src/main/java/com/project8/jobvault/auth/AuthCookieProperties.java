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

    @NotBlank
    private String path;

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

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
