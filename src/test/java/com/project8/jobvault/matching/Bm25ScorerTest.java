package com.project8.jobvault.matching;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25ScorerTest {
    private static final double EPSILON = 1e-9;

    @Test
    void computesSmoothedBm25Idf() {
        Map<String, Double> idf = Bm25Scorer.computeIdf(List.of(
                List.of("java", "spring"),
                List.of("java", "kafka"),
                List.of("cobol")));

        assertEquals(Math.log(1.0 + (3 - 2 + 0.5) / (2 + 0.5)), idf.get("java"), EPSILON);
        assertTrue(idf.get("cobol") > idf.get("java"));
    }

    @Test
    void termFrequencySaturates() {
        Map<String, Double> idf = Map.of("java", 1.0);
        double once = Bm25Scorer.score(List.of("java"), List.of("java"), idf, 1.0).value();
        double repeated = Bm25Scorer.score(
                List.of("java"), List.of("java", "java", "java", "java"), idf, 4.0).value();

        assertTrue(repeated > once);
        assertTrue(repeated < 1.0);
    }

    @Test
    void normalizesLongerDocumentsAgainstAverageLength() {
        Map<String, Double> idf = Map.of("java", 1.0);
        double shortDocument = Bm25Scorer.score(
                List.of("java"), List.of("java"), idf, 2.0).value();
        double longDocument = Bm25Scorer.score(
                List.of("java"), List.of("java", "other", "other", "other"), idf, 2.0).value();

        assertTrue(shortDocument > longDocument);
    }

    @Test
    void boundsScoresAndHandlesUnknownQueries() {
        Bm25Scorer.Result result = Bm25Scorer.score(
                List.of("java", "java_spring"), List.of("java", "spring"), Map.of("java", 1.0), 2.0);
        Bm25Scorer.Result unknown = Bm25Scorer.score(
                List.of("python"), List.of("java"), Map.of("java", 1.0), 1.0);

        assertTrue(result.available());
        assertTrue(result.value() >= 0.0 && result.value() <= 1.0);
        assertFalse(unknown.available());
        assertEquals(0.0, unknown.value(), EPSILON);
    }

    @Test
    void emptyCorpusProducesNoIdf() {
        assertTrue(Bm25Scorer.computeIdf(List.of()).isEmpty());
        assertEquals(0.0, Bm25Scorer.averageDocumentLength(List.of()), EPSILON);
    }
}
