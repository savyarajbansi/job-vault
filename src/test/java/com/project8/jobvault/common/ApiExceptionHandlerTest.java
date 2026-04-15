package com.project8.jobvault.common;

import com.project8.jobvault.auth.AuthErrorCodes;
import com.project8.jobvault.auth.AuthErrorException;
import com.project8.jobvault.resumes.UploadErrorCodes;
import com.project8.jobvault.resumes.UploadErrorException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsAuthErrorDetailsToEmptyMap() {
        ApiExceptionHandler handler = new ApiExceptionHandler(clock);
        AuthErrorException ex = new AuthErrorException(
                AuthErrorCodes.INVALID_TOKEN,
                AuthErrorCodes.MESSAGE_INVALID_TOKEN,
                HttpStatus.UNAUTHORIZED);

        ApiError error = handler.handleAuthError(ex).getBody();
        assertNotNull(error);
        assertNotNull(error.details());
        assertTrue(error.details().isEmpty());
    }

    @Test
    void defaultsBadCredentialsDetailsToEmptyMap() {
        ApiExceptionHandler handler = new ApiExceptionHandler(clock);

        ApiError error = handler.handleBadCredentials(new BadCredentialsException("bad")).getBody();
        assertNotNull(error);
        assertNotNull(error.details());
        assertTrue(error.details().isEmpty());
    }

    @Test
    void defaultsUploadErrorDetailsToEmptyMap() {
        ApiExceptionHandler handler = new ApiExceptionHandler(clock);
        UploadErrorException ex = new UploadErrorException(
                UploadErrorCodes.UPLOAD_FAILED,
                UploadErrorCodes.MESSAGE_UPLOAD_FAILED,
                HttpStatus.INTERNAL_SERVER_ERROR);

        ApiError error = handler.handleUploadError(ex).getBody();
        assertNotNull(error);
        assertNotNull(error.details());
        assertTrue(error.details().isEmpty());
    }
}
