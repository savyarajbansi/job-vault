package com.project8.jobvault.matching;

import java.util.List;

public record ScoredMatch(
        double overallScore,
        MatchFactorBreakdown factors,
        List<String> missingSkills) {
    public ScoredMatch {
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
    }
}
