package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class CorpusIdfService {
    private volatile Map<String, Double> idfByTerm = Map.of();

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final TextTokenizer tokenizer = new TextTokenizer(MatchingStopwords.DEFAULT);

    public CorpusIdfService(ObjectProvider<JobRepository> jobRepositoryProvider) {
        this.jobRepositoryProvider = jobRepositoryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAtStartup() {
        rebuildFromRepository();
    }

    public synchronized void rebuildFromRepository() {
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

    public synchronized void rebuild(List<String> allJobDescriptions) {
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
