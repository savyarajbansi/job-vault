package com.project8.jobvault.matching;

public record MatchFactorBreakdown(
        double cosine,
        double skillsOverlap,
        double experience,
        double location,
        boolean cosineAvailable,
        boolean skillsAvailable,
        boolean experienceAvailable,
        boolean locationAvailable) {

    public MatchFactorBreakdown(
            double cosine,
            double skillsOverlap,
            double experience,
            double location) {
        this(cosine, skillsOverlap, experience, location, true, true, true, true);
    }
}
