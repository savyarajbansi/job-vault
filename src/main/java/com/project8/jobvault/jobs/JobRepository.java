package com.project8.jobvault.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findAllByStatusOrderByCreatedAtDesc(JobStatus status);

    Optional<Job> findByIdAndStatus(UUID id, JobStatus status);
}
