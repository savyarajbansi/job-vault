package com.project8.jobvault.matching;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
    void targetCoverageIgnoresExtraResumeTerms() {
        Map<String, Double> idf = Map.of("java", 1.0);
        double focused = Bm25Scorer.score(List.of("java"), List.of("java"), idf, 1.0).value();
        double noisyResume = Bm25Scorer.score(
                List.of("java", "unrelated", "terms"), List.of("java"), idf, 1.0).value();

        assertEquals(1.0, focused, EPSILON);
        assertEquals(focused, noisyResume, EPSILON);
    }

    @Test
    void unmatchedDocumentTermsDoNotDiluteAUsableMatch() {
        Map<String, Double> idf = Map.of("java", 1.0);
        double shortDocument = Bm25Scorer.score(
                List.of("java"), List.of("java"), idf, 2.0).value();
        double longDocument = Bm25Scorer.score(
                List.of("java"), List.of("java", "other", "other", "other"), idf, 2.0).value();

        assertEquals(shortDocument, longDocument, EPSILON);
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
    void partialTargetCoverageRemainsBounded() {
        Map<String, Double> idf = Map.of("java", 1.0, "spring", 1.0);
        Bm25Scorer.Result result = Bm25Scorer.score(
                List.of("java"), List.of("java", "spring"), idf, 2.0);

        assertTrue(result.available());
        assertTrue(result.value() > 0.0 && result.value() < 1.0);
    }

    @Test
    void knownCorpusQueryWithNoTargetOverlapIsAnAvailableZero() {
        Bm25Scorer.Result result = Bm25Scorer.score(
                List.of("java"), List.of("python"), Map.of("java", 1.0, "python", 1.0), 1.0);

        assertTrue(result.available());
        assertEquals(0.0, result.value(), EPSILON);
    }

    @Test
    void suppliedResumeProfilesKeepTheExpectedLexicalOrdering() {
        List<String> jobTerms = List.of(
                "java", "spring", "rest", "microservices", "postgresql", "redis", "kafka",
                "docker", "aws", "ci/cd", "junit", "sql", "hibernate");
        Map<String, Double> idf = Bm25Scorer.computeIdf(List.of(jobTerms, List.of("other")));
        double averageLength = Bm25Scorer.averageDocumentLength(List.of(jobTerms, List.of("other")));

        Bm25Scorer.Result perfect = Bm25Scorer.score(jobTerms, jobTerms, idf, averageLength);
        Bm25Scorer.Result good = Bm25Scorer.score(
                List.of("java", "spring", "rest", "microservices", "postgresql", "docker", "aws", "ci/cd", "junit", "sql", "hibernate"),
                jobTerms, idf, averageLength);
        Bm25Scorer.Result partial = Bm25Scorer.score(
                List.of("rest", "postgresql", "redis", "docker", "aws", "ci/cd", "sql"),
                jobTerms, idf, averageLength);
        Bm25Scorer.Result poor = Bm25Scorer.score(
                List.of("react", "typescript", "next.js", "css", "graphql"), jobTerms, idf, averageLength);

        assertTrue(Stream.of(perfect, good, partial).allMatch(Bm25Scorer.Result::available));
        assertFalse(poor.available());
        assertTrue(perfect.value() > good.value());
        assertTrue(good.value() > partial.value());
        assertTrue(partial.value() > poor.value());
    }

    @Test
    void emptyCorpusProducesNoIdf() {
        assertTrue(Bm25Scorer.computeIdf(List.of()).isEmpty());
        assertEquals(0.0, Bm25Scorer.averageDocumentLength(List.of()), EPSILON);
    }
}
