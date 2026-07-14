package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorpusIdfServiceTest {

    @Test
    void rebuildFromRepositoryUsesActiveJobDescriptionsAsCorpus() {
        JobRepository repository = mock(JobRepository.class);
        when(repository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE)).thenReturn(List.of(
                job("Java Spring microservices"),
                job("Java Kafka distributed systems"),
                job("COBOL mainframe batch processing")));

        CorpusIdfService service = new CorpusIdfService(objectProvider(repository));
        service.rebuildFromRepository();

        Map<String, Double> idf = service.getIdf();
        assertTrue(idf.get("cobol") > idf.get("java"));
    }

    @Test
    void rebuildHandlesEmptyCorpus() {
        JobRepository repository = mock(JobRepository.class);
        when(repository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE)).thenReturn(List.of());

        CorpusIdfService service = new CorpusIdfService(objectProvider(repository));
        service.rebuildFromRepository();

        assertEquals(Map.of(), service.getIdf());
    }

    private ObjectProvider<JobRepository> objectProvider(JobRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("jobRepository", repository);
        return beanFactory.getBeanProvider(JobRepository.class);
    }

    private Job job(String description) {
        Job job = new TestJob();
        job.setDescription(description);
        return job;
    }

    static final class TestJob extends Job {
    }
}
