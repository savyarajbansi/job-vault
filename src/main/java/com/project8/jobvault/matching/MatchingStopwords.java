package com.project8.jobvault.matching;

import java.util.Set;

final class MatchingStopwords {
    static final Set<String> DEFAULT = Set.of(
            "the", "a", "an", "and", "or", "for", "to", "of", "in", "on", "with");

    private MatchingStopwords() {
    }
}
