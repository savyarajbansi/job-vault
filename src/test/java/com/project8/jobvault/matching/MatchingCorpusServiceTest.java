package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingCorpusServiceTest {
    @Test
    void rebuildUsesOnlyActiveJobsAndCachesTokensAndEmbeddings() {
        JobRepository repository = mock(JobRepository.class);
        Job first = job("Java Spring microservices");
        Job second = job("Java Kafka distributed systems");
        when(repository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE)).thenReturn(List.of(first, second));
        MatchingTextPreprocessor preprocessor = mock(MatchingTextPreprocessor.class);
        when(preprocessor.tokenize(anyString())).thenAnswer(invocation ->
                List.of(invocation.getArgument(0, String.class).toLowerCase().split(" ")));
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(anyString())).thenReturn(Optional.of(new double[] { 1.0, 0.0 }));
        when(embeddings.fingerprint()).thenReturn("test-model");

        MatchingCorpusService service = new MatchingCorpusService(
                objectProvider(repository), preprocessor, embeddings);
        service.rebuildFromRepository();

        MatchingCorpusService.CorpusSnapshot snapshot = service.getSnapshot();
        assertEquals(2, snapshot.jobs().size());
        assertTrue(snapshot.idfByTerm().containsKey("java"));
        assertNotNull(snapshot.jobs().get(first.getId()).embedding());
        assertEquals("test-model", snapshot.embeddingFingerprint());
    }

    @Test
    void rebuildHandlesEmptyCorpus() {
        JobRepository repository = mock(JobRepository.class);
        when(repository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE)).thenReturn(List.of());
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.fingerprint()).thenReturn("test-model");

        MatchingCorpusService service = new MatchingCorpusService(
                objectProvider(repository), mock(MatchingTextPreprocessor.class), embeddings);
        service.rebuildFromRepository();

        assertTrue(service.getSnapshot().idfByTerm().isEmpty());
        assertTrue(service.getSnapshot().jobs().isEmpty());
    }

    private ObjectProvider<JobRepository> objectProvider(JobRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("jobRepository", repository);
        return beanFactory.getBeanProvider(JobRepository.class);
    }

    private Job job(String text) {
        Job job = new TestJob();
        job.setId(UUID.randomUUID());
        job.setTitle(text.split(" ", 2)[0]);
        job.setDescription(text.substring(text.indexOf(' ') + 1));
        return job;
    }

    static final class TestJob extends Job {
    }
}
