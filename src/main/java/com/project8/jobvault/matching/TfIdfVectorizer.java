package com.project8.jobvault.matching;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TfIdfVectorizer {
    private final Map<String, Double> idfByTerm;

    public TfIdfVectorizer(Map<String, Double> idfByTerm) {
        this.idfByTerm = Map.copyOf(Objects.requireNonNull(idfByTerm, "idfByTerm"));
    }

    public Map<String, Double> vectorize(List<String> tokens) {
        Map<String, Double> tf = TermFrequency.compute(tokens);
        if (tf.isEmpty() || idfByTerm.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            Double idf = idfByTerm.get(entry.getKey());
            if (idf == null) {
                continue;
            }
            double weight = entry.getValue() * idf;
            if (weight > 0.0) {
                vector.put(entry.getKey(), weight);
            }
        }
        return vector;
    }
}
