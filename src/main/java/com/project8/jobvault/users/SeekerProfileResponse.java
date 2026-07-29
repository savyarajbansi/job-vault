package com.project8.jobvault.users;

import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.matching.WorkMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeekerProfileResponse(
        UUID userId,
        String displayName,
        String email,
        List<String> preferredSectors,
        String preferredLocation,
        WorkMode workMode,
        Integer yearsExperience,
        CurrentResume resume) {

    public record CurrentResume(
            UUID resumeId,
            String originalFilename,
            ResumeProcessingStatus status,
            Instant parsedAt,
            List<String> skills) {
    }
}
