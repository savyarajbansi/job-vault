package com.project8.jobvault.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class PublicJobController {
    private final JobRepository jobRepository;

    public PublicJobController(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @GetMapping
    public List<JobSummaryResponse> listPublished() {
        return jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.PUBLISHED).stream()
                .map(job -> new JobSummaryResponse(
                        job.getId(),
                        job.getTitle(),
                        job.getStatus(),
                        job.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobDetailResponse> getPublished(@PathVariable UUID jobId) {
        Optional<Job> job = jobRepository.findByIdAndStatus(jobId, JobStatus.PUBLISHED);
        return job.map(value -> ResponseEntity.ok(new JobDetailResponse(
                value.getId(),
                value.getTitle(),
                value.getDescription(),
                value.getStatus(),
                value.getCreatedAt(),
                value.getUpdatedAt(),
                value.getPublishedAt(),
                value.getDisabledAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
