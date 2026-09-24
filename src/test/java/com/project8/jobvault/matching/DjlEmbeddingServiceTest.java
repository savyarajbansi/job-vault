package com.project8.jobvault.matching;

import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DjlEmbeddingServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingModelAssetsKeepSemanticMatchingUnavailable() {
        DjlEmbeddingService service = new DjlEmbeddingService(
                true,
                temporaryDirectory.resolve("missing-model").toString(),
                256);

        assertTrue(service.embed("backend engineer").isEmpty());
        assertTrue(service.fingerprint().startsWith("unavailable:"));
    }

    @Test
    void disabledEmbeddingDoesNotAttemptToLoadModel() {
        DjlEmbeddingService service = new DjlEmbeddingService(
                false,
                temporaryDirectory.resolve("missing-model").toString(),
                256);

        assertTrue(service.embed("backend engineer").isEmpty());
    }

    @Test
    void installedLocalModelProducesSemanticEmbedding() {
        Path modelDirectory = Path.of("models/all-MiniLM-L6-v2");
        assumeTrue(Files.isDirectory(modelDirectory), "Local embedding model is not installed");

        DjlEmbeddingService service = new DjlEmbeddingService(true, modelDirectory.toString(), 256);

        assertTrue(service.embed("backend engineer with Java and Spring").isPresent());
    }
}
