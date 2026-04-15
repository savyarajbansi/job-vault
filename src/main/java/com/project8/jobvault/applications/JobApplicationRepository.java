package com.project8.jobvault.applications;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Optional<JobApplication> findByJobIdAndSeekerId(UUID jobId, UUID seekerId);

    Optional<JobApplication> findByIdAndJobEmployerId(UUID id, UUID employerId);
}
