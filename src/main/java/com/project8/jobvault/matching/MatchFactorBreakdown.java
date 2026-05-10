package com.project8.jobvault.matching;

public record MatchFactorBreakdown(
        double cosine,
        double skillsOverlap,
        double experience,
        double location) {
}
