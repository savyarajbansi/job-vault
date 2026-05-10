package com.project8.jobvault.matching;

public record MatchPage(
        int limit,
        int offset,
        int total) {
}
