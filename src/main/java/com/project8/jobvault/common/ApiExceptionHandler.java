package com.project8.jobvault.common;

import com.project8.jobvault.auth.AuthErrorCodes;
import com.project8.jobvault.auth.AuthErrorException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AuthErrorException.class)
    public ResponseEntity<ApiError> handleAuthError(AuthErrorException ex) {
        ApiError error = ApiError.of(ex.getCode(), ex.getMessage(), ex.getDetails(), clock.instant());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        ApiError error = ApiError.of(
                AuthErrorCodes.INVALID_TOKEN,
                AuthErrorCodes.MESSAGE_INVALID_TOKEN,
                null,
                clock.instant());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                message = "invalid";
            }
            fieldErrors.put(fieldError.getField(), message);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "validation_failed");
        details.put("fields", fieldErrors);

        ApiError error = ApiError.of(
                AuthErrorCodes.INVALID_TOKEN,
                AuthErrorCodes.MESSAGE_INVALID_TOKEN,
                details,
                clock.instant());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
