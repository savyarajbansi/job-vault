package com.project8.jobvault.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jobvault.security.jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;

    @NotBlank
    private String refreshHashSecret;

    @Positive
    private long accessTokenMinutes;

    @Positive
    private long refreshTokenDays;

    @Positive
    private int refreshTokenBytes;

    @Positive
    private int csrfTokenBytes;

    @Positive
    private int maxSessions;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getRefreshHashSecret() {
        return refreshHashSecret;
    }

    public void setRefreshHashSecret(String refreshHashSecret) {
        this.refreshHashSecret = refreshHashSecret;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(long refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public int getRefreshTokenBytes() {
        return refreshTokenBytes;
    }

    public void setRefreshTokenBytes(int refreshTokenBytes) {
        this.refreshTokenBytes = refreshTokenBytes;
    }

    public int getCsrfTokenBytes() {
        return csrfTokenBytes;
    }

    public void setCsrfTokenBytes(int csrfTokenBytes) {
        this.csrfTokenBytes = csrfTokenBytes;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }
}
