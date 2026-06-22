package com.project8.jobvault.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobDetailResponse(
        UUID id,
        String title,
        String description,
        String companyName,
        String location,
        Boolean remoteEligible,
        Integer minExperienceYears,
        Integer salaryMin,
        Integer salaryMax,
        EducationRequirement educationRequirement,
        List<String> requiredSkills,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant disabledAt) {
}
