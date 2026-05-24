package com.project8.jobvault.admin;

import java.time.Instant;
import java.util.Map;

public record AdminMetricsResponse(
        ParseMetrics parse,
        MatchMetrics match) {
    public record ParseMetrics(
            long totalAttempts,
            long successCount,
            long failureCount,
            Instant lastAttemptAt,
            Map<String, Long> failuresByCode) {
    }

    public record MatchMetrics(
            long totalAttempts,
            long successCount,
            long failureCount,
            Instant lastAttemptAt,
            Map<String, Long> failuresByCode) {
    }
}
