package com.project8.jobvault.jobs;

import com.project8.jobvault.matching.WorkMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobDetailResponse(
        UUID id,
        String title,
        String description,
        String companyName,
        List<String> sectorTags,
        String location,
        WorkMode workMode,
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
