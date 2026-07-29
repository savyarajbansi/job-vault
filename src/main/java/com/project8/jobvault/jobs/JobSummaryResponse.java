package com.project8.jobvault.jobs;

import com.project8.jobvault.matching.WorkMode;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String companyName,
        List<String> sectorTags,
        String location,
        WorkMode workMode,
        Integer minExperienceYears,
        Integer salaryMin,
        Integer salaryMax,
        JobStatus status,
        Instant createdAt) {
}
