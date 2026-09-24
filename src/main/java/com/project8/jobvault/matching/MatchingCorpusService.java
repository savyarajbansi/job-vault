package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@EnableAsync
public class MatchingCorpusService {
    private static final Logger log = LoggerFactory.getLogger(MatchingCorpusService.class);

    private volatile CorpusSnapshot snapshot = CorpusSnapshot.empty();
    private final AtomicBoolean rebuildInProgress = new AtomicBoolean();
    private final AtomicBoolean rebuildRequested = new AtomicBoolean();

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final MatchingTextPreprocessor textPreprocessor;
    private final EmbeddingService embeddingService;

    public MatchingCorpusService(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            MatchingTextPreprocessor textPreprocessor,
            EmbeddingService embeddingService) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.textPreprocessor = textPreprocessor;
        this.embeddingService = embeddingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAtStartup() {
        rebuildNow();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCorpusRebuildEvent(CorpusRebuildEvent event) {
        log.debug("Rebuilding matching corpus after commit (reason: {})", event.reason());
        requestAsyncRebuild();
    }

    @Scheduled(fixedDelayString = "${jobvault.matching.corpus-rebuild-interval-ms:300000}")
    public void scheduledRebuild() {
        requestAsyncRebuild();
    }

    public synchronized void rebuildFromRepository() {
        rebuildNow();
    }

    private void rebuildNow() {
        JobRepository jobRepository = jobRepositoryProvider.getIfAvailable();
        if (jobRepository == null) {
            snapshot = CorpusSnapshot.empty();
            return;
        }
        List<Job> jobs = jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE);
        if (jobs == null || jobs.isEmpty()) {
            snapshot = CorpusSnapshot.empty();
            return;
        }

        List<JobDocument> documents = new ArrayList<>(jobs.size());
        for (Job job : jobs) {
            if (job == null) {
                continue;
            }
            String title = job.getTitle() == null ? "" : job.getTitle();
            String description = job.getDescription() == null ? "" : job.getDescription();
            String text = title + " " + description;
            documents.add(new JobDocument(
                    job.getId(),
                    textPreprocessor.tokenize(title),
                    textPreprocessor.tokenize(description),
                    embeddingService.embed(text).orElse(null)));
        }
        documents.sort(Comparator.comparing(document -> document.id() == null ? "" : document.id().toString()));
        rebuild(documents, fingerprintDocuments(documents));
    }

    private synchronized void rebuild(List<JobDocument> documents, String fingerprint) {
        if (documents == null || documents.isEmpty()) {
            snapshot = CorpusSnapshot.empty();
            return;
        }
        List<List<String>> corpus = documents.stream()
                .map(document -> combine(document.titleTokens(), document.descriptionTokens()))
                .toList();
        Map<UUID, JobDocument> byId = new LinkedHashMap<>();
        for (JobDocument document : documents) {
            if (document.id() != null) {
                byId.put(document.id(), document);
            }
        }
        snapshot = new CorpusSnapshot(
                Bm25Scorer.computeIdf(corpus),
                Bm25Scorer.averageDocumentLength(documents.stream()
                        .map(JobDocument::titleTokens)
                        .toList()),
                Bm25Scorer.averageDocumentLength(documents.stream()
                        .map(JobDocument::descriptionTokens)
                        .toList()),
                byId,
                fingerprint,
                embeddingService.fingerprint());
    }

    public CorpusSnapshot getSnapshot() {
        return snapshot;
    }

    private void requestAsyncRebuild() {
        rebuildRequested.set(true);
        if (!rebuildInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            do {
                rebuildRequested.set(false);
                rebuildNow();
            } while (rebuildRequested.get());
        } finally {
            rebuildInProgress.set(false);
        }
    }

    private String fingerprintDocuments(List<JobDocument> documents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (JobDocument document : documents) {
                digest.update((document.id() == null ? "" : document.id().toString())
                        .getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(document.titleTokens().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(document.descriptionTokens().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private List<String> combine(List<String> titleTokens, List<String> descriptionTokens) {
        List<String> combined = new ArrayList<>();
        if (titleTokens != null) {
            combined.addAll(titleTokens);
        }
        if (descriptionTokens != null) {
            combined.addAll(descriptionTokens);
        }
        return List.copyOf(combined);
    }

    public record JobDocument(UUID id, List<String> titleTokens, List<String> descriptionTokens, double[] embedding) {
        public JobDocument {
            titleTokens = titleTokens == null ? List.of() : List.copyOf(titleTokens);
            descriptionTokens = descriptionTokens == null ? List.of() : List.copyOf(descriptionTokens);
            embedding = embedding == null ? null : embedding.clone();
        }

        public JobDocument(UUID id, List<String> tokens, double[] embedding) {
            this(id, tokens, List.of(), embedding);
        }

        public List<String> tokens() {
            List<String> combined = new ArrayList<>(titleTokens);
            combined.addAll(descriptionTokens);
            return List.copyOf(combined);
        }

        @Override
        public double[] embedding() {
            return embedding == null ? null : embedding.clone();
        }
    }

    public record CorpusSnapshot(
            Map<String, Double> idfByTerm,
            double averageTitleLength,
            double averageDescriptionLength,
            Map<UUID, JobDocument> jobs,
            String fingerprint,
            String embeddingFingerprint) {
        public CorpusSnapshot {
            idfByTerm = idfByTerm == null ? Map.of() : Map.copyOf(idfByTerm);
            jobs = jobs == null ? Map.of() : Map.copyOf(jobs);
            fingerprint = fingerprint == null ? "empty" : fingerprint;
            embeddingFingerprint = embeddingFingerprint == null ? "unknown" : embeddingFingerprint;
        }

        public CorpusSnapshot(
                Map<String, Double> idfByTerm,
                double averageDocumentLength,
                Map<UUID, JobDocument> jobs,
                String fingerprint,
                String embeddingFingerprint) {
            this(idfByTerm, averageDocumentLength, averageDocumentLength, jobs, fingerprint, embeddingFingerprint);
        }

        public double averageDocumentLength() {
            return averageTitleLength + averageDescriptionLength;
        }

        public static CorpusSnapshot empty() {
            return new CorpusSnapshot(Map.of(), 0.0, 0.0, Map.of(), "empty", "unavailable");
        }
    }
}
