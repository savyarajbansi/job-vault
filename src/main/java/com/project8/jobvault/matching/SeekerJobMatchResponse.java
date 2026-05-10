package com.project8.jobvault.matching;

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
            boolean remoteEligible) {
    }
}
