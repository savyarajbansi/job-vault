package com.project8.jobvault.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details, Instant timestamp) {
    public static ApiError of(String code, String message, Map<String, Object> details, Instant timestamp) {
        return new ApiError(code, message, details, timestamp);
    }
}
