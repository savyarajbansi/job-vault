package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
public class CorpusIdfService {

    private static final Logger log = LoggerFactory.getLogger(CorpusIdfService.class);

    private volatile CorpusSnapshot snapshot = new CorpusSnapshot(Map.of(), "empty");
    private final AtomicBoolean rebuildInProgress = new AtomicBoolean();
    private final AtomicBoolean rebuildRequested = new AtomicBoolean();

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final TextTokenizer tokenizer = new TextTokenizer(MatchingStopwords.DEFAULT);

    public CorpusIdfService(ObjectProvider<JobRepository> jobRepositoryProvider) {
        this.jobRepositoryProvider = jobRepositoryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAtStartup() {
        rebuildNow();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCorpusRebuildEvent(CorpusRebuildEvent event) {
        log.debug("Rebuilding IDF corpus after commit (reason: {})", event.reason());
        requestAsyncRebuild();
    }

    @Scheduled(fixedDelayString = "${jobvault.matching.idf-rebuild-interval-ms:300000}")
    public void scheduledRebuild() {
        requestAsyncRebuild();
    }

    /**
     * Rebuilds synchronously on the calling thread.
     * Prefer CorpusRebuildEventPublisher#publishRebuild from transactional
     * code so the rebuild runs after commit without blocking the response.
     */
    public synchronized void rebuildFromRepository() {
        rebuildNow();
    }

    private void rebuildNow() {
        JobRepository jobRepository = jobRepositoryProvider.getIfAvailable();
        if (jobRepository == null) {
            snapshot = new CorpusSnapshot(Map.of(), "empty");
            return;
        }
        List<Job> jobs = jobRepository.findAllByStatusOrderByCreatedAtDesc(
                com.project8.jobvault.jobs.JobStatus.ACTIVE);
        if (jobs == null || jobs.isEmpty()) {
            snapshot = new CorpusSnapshot(Map.of(), "empty");
            return;
        }
        List<JobDocument> documents = new ArrayList<>(jobs.size());
        for (Job job : jobs) {
            if (job == null) {
                continue;
            }
            String text = (job.getTitle() == null ? "" : job.getTitle())
                    + " " + (job.getDescription() == null ? "" : job.getDescription());
            documents.add(new JobDocument(job.getId() == null ? "" : job.getId().toString(), text));
        }
        documents.sort(Comparator.comparing(JobDocument::id));
        List<String> descriptions = documents.stream().map(JobDocument::text).toList();
        rebuild(descriptions, fingerprintDocuments(documents));
    }

    synchronized void rebuild(List<String> allJobDescriptions) {
        rebuild(allJobDescriptions, fingerprintValues(allJobDescriptions));
    }

    private synchronized void rebuild(List<String> allJobDescriptions, String fingerprint) {
        if (allJobDescriptions == null || allJobDescriptions.isEmpty()) {
            snapshot = new CorpusSnapshot(Map.of(), "empty");
            return;
        }
        List<List<String>> corpus = new ArrayList<>(allJobDescriptions.size());
        for (String description : allJobDescriptions) {
            corpus.add(tokenizer.tokenize(description));
        }
        snapshot = new CorpusSnapshot(InverseDocumentFrequency.compute(corpus), fingerprint);
    }

    public Map<String, Double> getIdf() {
        return snapshot.idfByTerm();
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
        List<String> values = documents.stream()
                .map(document -> document.id() + "\u0000" + document.text())
                .toList();
        return fingerprintValues(values);
    }

    private String fingerprintValues(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
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

    public record CorpusSnapshot(Map<String, Double> idfByTerm, String fingerprint) {
        public CorpusSnapshot {
            idfByTerm = idfByTerm == null ? Map.of() : Map.copyOf(idfByTerm);
            fingerprint = fingerprint == null ? "empty" : fingerprint;
        }
    }

    private record JobDocument(String id, String text) {
    }
}
