package com.project8.jobvault.matching;

public record MatchFactorBreakdown(
        double bm25,
        double embedding,
        double experience,
        double location,
        double salary,
        boolean bm25Available,
        boolean embeddingAvailable,
        boolean experienceAvailable,
        boolean locationAvailable,
        boolean salaryAvailable,
        LexicalBreakdown lexical) {

    public MatchFactorBreakdown {
        lexical = lexical == null ? LexicalBreakdown.empty() : lexical;
    }

    public MatchFactorBreakdown(
            double bm25,
            double embedding,
            double experience,
            double location) {
        this(bm25, embedding, experience, location, 0.0, true, true, true, true, false,
                LexicalBreakdown.empty());
    }

    public record LexicalBreakdown(
            double requiredSkills,
            double title,
            double description,
            boolean requiredSkillsAvailable,
            boolean titleAvailable,
            boolean descriptionAvailable) {

        public static LexicalBreakdown empty() {
            return new LexicalBreakdown(0.0, 0.0, 0.0, false, false, false);
        }
    }
}
