package com.project8.jobvault.matching;

import java.util.Map;
import java.util.Objects;

public final class CosineSimilarity {
    private CosineSimilarity() {
    }

    public static double compute(Map<String, Double> left, Map<String, Double> right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        double normLeft = norm(left);
        double normRight = norm(right);
        if (normLeft == 0.0 || normRight == 0.0) {
            return 0.0;
        }

        Map<String, Double> smaller = left.size() <= right.size() ? left : right;
        Map<String, Double> larger = left.size() <= right.size() ? right : left;

        double dot = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            Double leftValue = entry.getValue();
            if (leftValue == null) {
                continue;
            }
            Double rightValue = larger.get(entry.getKey());
            if (rightValue == null) {
                continue;
            }
            dot += leftValue * rightValue;
        }

        return dot / (normLeft * normRight);
    }

    private static double norm(Map<String, Double> vector) {
        double sum = 0.0;
        for (Double value : vector.values()) {
            if (value == null) {
                continue;
            }
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
