package com.project8.jobvault.matching;

public record MatchFactorBreakdown(
        double bm25,
        double embedding,
        double experience,
        double location,
        boolean bm25Available,
        boolean embeddingAvailable,
        boolean experienceAvailable,
        boolean locationAvailable) {

    public MatchFactorBreakdown(
            double bm25,
            double embedding,
            double experience,
            double location) {
        this(bm25, embedding, experience, location, true, true, true, true);
    }
}
