package com.project8.jobvault.jobs;

import java.time.Instant;
import java.util.UUID;

public record JobDetailResponse(
        UUID id,
        String title,
        String description,
        String location,
        Boolean remoteEligible,
        Integer minExperienceYears,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant disabledAt) {
}
