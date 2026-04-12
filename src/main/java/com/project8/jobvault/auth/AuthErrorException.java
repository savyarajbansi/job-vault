package com.project8.jobvault.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class AuthErrorException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public AuthErrorException(String code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public AuthErrorException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
