package com.project8.jobvault.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class InverseDocumentFrequency {
    private static final double SMOOTHING = 1.0;

    private InverseDocumentFrequency() {
    }

    public static Map<String, Double> compute(List<List<String>> corpus) {
        Objects.requireNonNull(corpus, "corpus");
        if (corpus.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> docFrequencies = new HashMap<>();
        for (List<String> document : corpus) {
            if (document == null || document.isEmpty()) {
                continue;
            }
            Set<String> uniqueTerms = new HashSet<>();
            for (String token : document) {
                if (token == null) {
                    continue;
                }
                String normalized = token.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                uniqueTerms.add(normalized);
            }
            for (String term : uniqueTerms) {
                docFrequencies.merge(term, 1,
                        (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right));
            }
        }

        if (docFrequencies.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> idf = new HashMap<>();
        double numerator = SMOOTHING + corpus.size();
        for (Map.Entry<String, Integer> entry : docFrequencies.entrySet()) {
            double denominator = SMOOTHING + entry.getValue();
            double score = Math.log(numerator / denominator) + SMOOTHING;
            idf.put(entry.getKey(), score);
        }
        return idf;
    }
}
