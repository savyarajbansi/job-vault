package com.project8.jobvault.resumes;

import java.util.UUID;

public record ResumeUploadResponse(
        UUID resumeId,
        ResumeProcessingStatus status) {
}
