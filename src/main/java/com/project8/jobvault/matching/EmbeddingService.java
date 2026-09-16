package com.project8.jobvault.matching;

import java.util.Optional;

/** Provides optional local semantic embeddings for matching. */
public interface EmbeddingService {
    Optional<double[]> embed(String text);

    String fingerprint();
}
