package com.project8.jobvault.jobs;

import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String companyName,
        String location,
        Boolean remoteEligible,
        Integer minExperienceYears,
        Integer salaryMin,
        Integer salaryMax,
        JobStatus status,
        Instant createdAt) {
}
