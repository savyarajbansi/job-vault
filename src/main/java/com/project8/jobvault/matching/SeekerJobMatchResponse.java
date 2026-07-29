package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.EducationRequirement;
import com.project8.jobvault.matching.WorkMode;
import java.util.List;
import java.util.UUID;

public record SeekerJobMatchResponse(
        List<SeekerJobMatchResponseItem> items,
        MatchPage page) {

    public record SeekerJobMatchResponseItem(
            UUID jobId,
            double score,
            MatchFactorBreakdown factors,
            JobInfo job,
            List<String> missingSkills) {
    }

    public record JobInfo(
            String title,
            String companyName,
            List<String> sectorTags,
            String location,
            WorkMode workMode,
            Integer salaryMin,
            Integer salaryMax,
            EducationRequirement educationRequirement,
            List<String> requiredSkills) {
    }
}
