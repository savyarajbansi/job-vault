package com.project8.jobvault.matching;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lazy DJL/ONNX Runtime embedding provider. Missing local model assets disable
 * this factor without taking lexical and structured matching offline.
 */
@Service
public class DjlEmbeddingService implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(DjlEmbeddingService.class);

    private final boolean enabled;
    private final Path modelDirectory;
    private final int maxTokenLength;
    private final Object loadLock = new Object();
    private volatile Predictor<String, float[]> predictor;
    private volatile boolean unavailable;
    private volatile boolean unavailableLogged;

    public DjlEmbeddingService(
            @Value("${jobvault.matching.embedding.enabled:true}") boolean enabled,
            @Value("${jobvault.matching.embedding.model-directory:models/all-MiniLM-L6-v2}") String modelDirectory,
            @Value("${jobvault.matching.embedding.max-token-length:256}") int maxTokenLength) {
        this.enabled = enabled;
        this.modelDirectory = Path.of(modelDirectory);
        this.maxTokenLength = Math.max(8, maxTokenLength);
    }

    @Override
    public Optional<double[]> embed(String text) {
        if (!enabled || unavailable || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            Predictor<String, float[]> current = predictor;
            if (current == null) {
                current = loadPredictor();
            }
            float[] raw = current.predict(text);
            return normalize(raw);
        } catch (Exception ex) {
            unavailable = true;
            logUnavailable(ex);
            return Optional.empty();
        }
    }

    @Override
    public String fingerprint() {
        try {
            if (!Files.exists(modelDirectory)) {
                return "unavailable:" + modelDirectory.toAbsolutePath();
            }
            return "djl-onnx:" + modelDirectory.toAbsolutePath()
                    + ":" + Files.getLastModifiedTime(modelDirectory).toMillis()
                    + ":max=" + maxTokenLength;
        } catch (Exception ex) {
            return "djl-onnx:" + modelDirectory.toAbsolutePath() + ":max=" + maxTokenLength;
        }
    }

    private Predictor<String, float[]> loadPredictor() throws Exception {
        synchronized (loadLock) {
            if (predictor != null) {
                return predictor;
            }
            if (!Files.isDirectory(modelDirectory)) {
                throw new IllegalStateException("Embedding model directory does not exist: " + modelDirectory);
            }
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(modelDirectory)
                    .optEngine("OnnxRuntime")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .optArgument("maxLength", maxTokenLength)
                    .build();
            ZooModel<String, float[]> model = criteria.loadModel();
            predictor = model.newPredictor();
            return predictor;
        }
    }

    private Optional<double[]> normalize(float[] values) {
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        double norm = 0.0;
        for (float value : values) {
            norm += (double) value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0 || !Double.isFinite(norm)) {
            return Optional.empty();
        }
        double[] normalized = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = values[i] / norm;
        }
        return Optional.of(normalized);
    }

    private void logUnavailable(Exception ex) {
        if (!unavailableLogged) {
            synchronized (loadLock) {
                if (!unavailableLogged) {
                    log.warn("Semantic matching disabled: {}", ex.getMessage());
                    unavailableLogged = true;
                }
            }
        }
    }
}
