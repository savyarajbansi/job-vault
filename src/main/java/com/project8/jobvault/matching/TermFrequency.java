package com.project8.jobvault.matching;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TermFrequency {
    private TermFrequency() {
    }

    public static Map<String, Double> compute(List<String> tokens) {
        Objects.requireNonNull(tokens, "tokens");
        if (tokens.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            counts.merge(normalized, 1,
                    (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right));
            total += 1;
        }

        if (total == 0) {
            return Map.of();
        }

        Map<String, Double> tf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            tf.put(entry.getKey(), entry.getValue() / (double) total);
        }
        return tf;
    }
}
