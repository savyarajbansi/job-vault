package com.project8.jobvault.resumes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeHistoryResponse(
        List<ResumeHistoryItem> items,
        ResumeHistoryPage page) {
    public record ResumeHistoryItem(
            UUID resumeId,
            String originalFilename,
            ResumeProcessingStatus status,
            String failureCode,
            Instant createdAt,
            Instant parsedAt) {
    }

    public record ResumeHistoryPage(
            int limit,
            int offset,
            int total) {
    }
}
