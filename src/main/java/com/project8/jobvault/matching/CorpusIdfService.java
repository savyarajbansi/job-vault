package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@EnableAsync
public class CorpusIdfService {

    private static final Logger log = LoggerFactory.getLogger(CorpusIdfService.class);

    private volatile Map<String, Double> idfByTerm = Map.of();

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
        rebuildNow();
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
            idfByTerm = Map.of();
            return;
        }
        List<Job> jobs = jobRepository.findAll();
        if (jobs == null || jobs.isEmpty()) {
            idfByTerm = Map.of();
            return;
        }
        List<String> descriptions = new ArrayList<>(jobs.size());
        for (Job job : jobs) {
            if (job == null) {
                continue;
            }
            descriptions.add(job.getDescription());
        }
        rebuild(descriptions);
    }

    synchronized void rebuild(List<String> allJobDescriptions) {
        if (allJobDescriptions == null || allJobDescriptions.isEmpty()) {
            idfByTerm = Map.of();
            return;
        }
        List<List<String>> corpus = new ArrayList<>(allJobDescriptions.size());
        for (String description : allJobDescriptions) {
            corpus.add(tokenizer.tokenize(description));
        }
        idfByTerm = InverseDocumentFrequency.compute(corpus);
    }

    public Map<String, Double> getIdf() {
        return idfByTerm;
    }
}