package com.project8.jobvault.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        boolean isRead,
        Instant createdAt) {
}
