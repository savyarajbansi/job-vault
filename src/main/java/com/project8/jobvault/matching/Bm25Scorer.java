package com.project8.jobvault.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small, deterministic Okapi BM25 implementation used by seeker matching. */
public final class Bm25Scorer {
    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    private Bm25Scorer() {
    }

    public static Map<String, Double> computeIdf(List<List<String>> corpus) {
        if (corpus == null || corpus.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> document : corpus) {
            if (document == null || document.isEmpty()) {
                continue;
            }
            Set<String> uniqueTerms = new HashSet<>();
            for (String token : document) {
                if (token != null && !token.isBlank()) {
                    uniqueTerms.add(token);
                }
            }
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        if (documentFrequency.isEmpty()) {
            return Map.of();
        }

        int documentCount = corpus.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double numerator = documentCount - entry.getValue() + 0.5;
            double denominator = entry.getValue() + 0.5;
            idf.put(entry.getKey(), Math.log(1.0 + numerator / denominator));
        }
        return Map.copyOf(idf);
    }

    public static double averageDocumentLength(List<List<String>> corpus) {
        if (corpus == null || corpus.isEmpty()) {
            return 0.0;
        }
        long total = corpus.stream()
                .mapToLong(document -> document == null ? 0 : document.size())
                .sum();
        return (double) total / corpus.size();
    }

    public static Result score(
            List<String> queryTokens,
            List<String> documentTokens,
            Map<String, Double> idfByTerm,
            double averageDocumentLength) {
        if (queryTokens == null || queryTokens.isEmpty()
                || documentTokens == null || documentTokens.isEmpty()
                || idfByTerm == null || idfByTerm.isEmpty()) {
            return new Result(0.0, false);
        }

        Set<String> queryTerms = new HashSet<>();
        for (String token : queryTokens) {
            if (token != null && !token.isBlank()) {
                queryTerms.add(token);
            }
        }
        if (queryTerms.isEmpty()) {
            return new Result(0.0, false);
        }
        boolean hasKnownQueryTerm = queryTerms.stream().anyMatch(idfByTerm::containsKey);
        if (!hasKnownQueryTerm) {
            return new Result(0.0, false);
        }

        Map<String, Integer> termFrequency = frequencies(documentTokens);
        double documentLength = documentTokens.size();
        double lengthNormalization = averageDocumentLength <= 0.0
                ? 1.0
                : 1.0 - DEFAULT_B + DEFAULT_B * documentLength / averageDocumentLength;
        double rawScore = 0.0;
        double theoreticalMaximum = 0.0;
        boolean hasOverlap = false;
        for (String term : termFrequency.keySet()) {
            double idf = idfByTerm.getOrDefault(term, 0.0);
            if (idf <= 0.0) {
                continue;
            }
            int frequency = termFrequency.get(term);
            double maximumNumerator = DEFAULT_K1 + 1.0;
            double maximumDenominator = 1.0 + DEFAULT_K1 * lengthNormalization;
            theoreticalMaximum += idf * maximumNumerator / maximumDenominator;
            if (!queryTerms.contains(term)) continue;
            hasOverlap = true;
            double numerator = frequency * (DEFAULT_K1 + 1.0);
            double denominator = frequency + DEFAULT_K1 * lengthNormalization;
            rawScore += idf * numerator / denominator;
        }

        if (theoreticalMaximum <= 0.0) {
            return new Result(0.0, false);
        }
        if (!hasOverlap) {
            return new Result(0.0, true);
        }
        return new Result(clamp01(rawScore / theoreticalMaximum), true);
    }

    private static Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                frequencies.merge(token, 1, Integer::sum);
            }
        }
        return frequencies;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Result(double value, boolean available) {
    }
}
