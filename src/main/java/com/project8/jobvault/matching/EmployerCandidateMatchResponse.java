package com.project8.jobvault.matching;

import java.util.List;
import java.util.UUID;

public record EmployerCandidateMatchResponse(
        List<EmployerCandidateMatchItem> items,
        MatchPage page) {
    public record EmployerCandidateMatchItem(
            UUID resumeId,
            UUID seekerId,
            String seekerName,
            double score,
            MatchFactorBreakdown factors,
            List<String> missingSkills) {
    }
}