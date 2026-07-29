package com.project8.jobvault.notifications;

import com.project8.jobvault.jobs.CandidateMatchStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        boolean isRead,
        Instant createdAt,
        UUID relatedJobId,
        UUID shortlistId,
        CandidateMatchStatus shortlistStatus) {
}
