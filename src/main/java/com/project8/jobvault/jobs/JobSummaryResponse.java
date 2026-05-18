package com.project8.jobvault.jobs;

import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String location,
        Boolean remoteEligible,
        Integer minExperienceYears,
        JobStatus status,
        Instant createdAt) {
}
