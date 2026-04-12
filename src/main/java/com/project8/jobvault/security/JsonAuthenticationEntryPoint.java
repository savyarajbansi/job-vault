package com.project8.jobvault.security;

import com.project8.jobvault.auth.AuthErrorCodes;
import com.project8.jobvault.common.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiErrorWriter errorWriter;

    public JsonAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        if (authException instanceof BadCredentialsException) {
            errorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                AuthErrorCodes.INVALID_TOKEN,
                AuthErrorCodes.MESSAGE_INVALID_TOKEN,
                null
            );
            return;
        }
        if (authException instanceof InsufficientAuthenticationException) {
            errorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                AuthErrorCodes.AUTH_REQUIRED,
                AuthErrorCodes.MESSAGE_AUTH_REQUIRED,
                null
            );
            return;
        }
        errorWriter.write(
            response,
            HttpStatus.UNAUTHORIZED,
            AuthErrorCodes.AUTH_REQUIRED,
            AuthErrorCodes.MESSAGE_AUTH_REQUIRED,
            null
        );
    }
}
