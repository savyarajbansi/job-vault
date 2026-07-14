package com.project8.jobvault.jobs;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateMatchNotificationRepository extends JpaRepository<CandidateMatchNotification, UUID> {
    boolean existsByJobIdAndSeekerId(UUID jobId, UUID seekerId);
}
