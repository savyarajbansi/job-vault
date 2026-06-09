package com.project8.jobvault.auth;

public final class AuthErrorCodes {
    public static final String AUTH_REQUIRED = "ERR_AUTH_001";
    public static final String INVALID_TOKEN = "ERR_AUTH_002";
    public static final String REFRESH_INVALID = "ERR_AUTH_003";
    public static final String REGISTRATION_INVALID = "ERR_AUTH_004";

    public static final String MESSAGE_AUTH_REQUIRED = "Authentication required.";
    public static final String MESSAGE_INVALID_TOKEN = "Invalid or expired token, or invalid authentication credentials/request payload.";
    public static final String MESSAGE_REFRESH_INVALID = "Refresh/logout auth failed: refresh token invalid/revoked/expired/reused or request-integrity check failed.";
    public static final String MESSAGE_REGISTRATION_INVALID = "Registration failed.";
    public static final String MESSAGE_REGISTRATION_EMAIL_EXISTS = "An account with this email already exists.";
    public static final String MESSAGE_REGISTRATION_INVALID_ROLE = "Registration failed: the selected role is not supported.";
    public static final String MESSAGE_REGISTRATION_ROLE_MISSING = "Registration is temporarily unavailable because required roles are not configured.";

    private AuthErrorCodes() {
    }
}
