package com.project8.jobvault.applications;

import java.time.Instant;
import java.util.UUID;

public record JobApplicationResponse(
        UUID id,
        UUID jobId,
        UUID seekerId,
        ApplicationStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        Instant decidedAt) {
}
