package com.project8.jobvault.applications;

import java.time.Instant;
import java.util.UUID;

public record SeekerApplicationResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        ApplicationStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        Instant decidedAt) {
}