package com.project8.jobvault.auth;

public final class AuthErrorCodes {
    public static final String AUTH_REQUIRED = "ERR_AUTH_001";
    public static final String INVALID_TOKEN = "ERR_AUTH_002";
    public static final String REFRESH_INVALID = "ERR_AUTH_003";

    public static final String MESSAGE_AUTH_REQUIRED = "Authentication required.";
    public static final String MESSAGE_INVALID_TOKEN = "Invalid or expired token.";
    public static final String MESSAGE_REFRESH_INVALID = "Refresh token invalid or revoked.";

    private AuthErrorCodes() {
    }
}
