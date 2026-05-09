package com.project8.jobvault.matching;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TfIdfMatchingTest {
    private static final double EPSILON = 1e-6;

    @Test
    void tokenizerNormalizesAndRemovesStopwords() {
        TextTokenizer tokenizer = new TextTokenizer(Set.of("the"));

        List<String> tokens = tokenizer.tokenize("The Quick, brown fox!");

        assertEquals(List.of("quick", "brown", "fox"), tokens);
    }

    @Test
    void computesTermFrequency() {
        List<String> tokens = List.of("apple", "banana", "apple");

        Map<String, Double> tf = TermFrequency.compute(tokens);

        assertEquals(2.0 / 3.0, tf.get("apple"), EPSILON);
        assertEquals(1.0 / 3.0, tf.get("banana"), EPSILON);
        assertEquals(2, tf.size());
    }

    @Test
    void computesInverseDocumentFrequencyWithSmoothing() {
        List<List<String>> corpus = buildCorpus();

        Map<String, Double> idf = InverseDocumentFrequency.compute(corpus);

        double expectedCommon = Math.log((1.0 + corpus.size()) / (1.0 + 2.0)) + 1.0;
        double expectedKiwi = Math.log((1.0 + corpus.size()) / (1.0 + 1.0)) + 1.0;

        assertEquals(expectedCommon, idf.get("apple"), EPSILON);
        assertEquals(expectedCommon, idf.get("banana"), EPSILON);
        assertEquals(expectedCommon, idf.get("orange"), EPSILON);
        assertEquals(expectedCommon, idf.get("fruit"), EPSILON);
        assertEquals(expectedKiwi, idf.get("kiwi"), EPSILON);
        assertTrue(idf.get("kiwi") > idf.get("apple"));
    }

    @Test
    void buildsTfIdfVector() {
        List<List<String>> corpus = buildCorpus();
        Map<String, Double> idf = InverseDocumentFrequency.compute(corpus);
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(idf);

        Map<String, Double> vector = vectorizer.vectorize(corpus.get(0));

        double expectedCommon = Math.log((1.0 + corpus.size()) / (1.0 + 2.0)) + 1.0;
        double expectedApple = (2.0 / 3.0) * expectedCommon;
        double expectedBanana = (1.0 / 3.0) * expectedCommon;

        assertEquals(expectedApple, vector.get("apple"), EPSILON);
        assertEquals(expectedBanana, vector.get("banana"), EPSILON);
        assertFalse(vector.containsKey("orange"));
    }

    @Test
    void cosineSimilarityHandlesEmptyVectors() {
        assertEquals(0.0, CosineSimilarity.compute(Map.of(), Map.of()), EPSILON);
    }

    @Test
    void computesCosineSimilarityForToyCorpus() {
        List<List<String>> corpus = buildCorpus();
        Map<String, Double> idf = InverseDocumentFrequency.compute(corpus);
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(idf);

        Map<String, Double> doc1Vector = vectorizer.vectorize(corpus.get(0));
        Map<String, Double> doc2Vector = vectorizer.vectorize(corpus.get(1));

        double expectedCommon = Math.log((1.0 + corpus.size()) / (1.0 + 2.0)) + 1.0;
        double expectedApple = (2.0 / 3.0) * expectedCommon;
        double expectedBanana = (1.0 / 3.0) * expectedCommon;
        double expectedBananaDoc2 = 0.5 * expectedCommon;
        double expectedOrangeDoc2 = 0.25 * expectedCommon;
        double expectedFruitDoc2 = 0.25 * expectedCommon;

        double expectedDot = expectedBanana * expectedBananaDoc2;
        double expectedNorm1 = Math.sqrt(expectedApple * expectedApple + expectedBanana * expectedBanana);
        double expectedNorm2 = Math.sqrt(
                expectedBananaDoc2 * expectedBananaDoc2
                        + expectedOrangeDoc2 * expectedOrangeDoc2
                        + expectedFruitDoc2 * expectedFruitDoc2);
        double expectedCosine = expectedDot / (expectedNorm1 * expectedNorm2);

        assertEquals(expectedCosine, CosineSimilarity.compute(doc1Vector, doc2Vector), EPSILON);
    }

    private List<List<String>> buildCorpus() {
        return List.of(
                List.of("apple", "banana", "apple"),
                List.of("banana", "orange", "banana", "fruit"),
                List.of("orange", "fruit", "apple", "kiwi"));
    }
}
