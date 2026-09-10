package com.project8.jobvault.jobs;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateMatchNotificationRepository extends JpaRepository<CandidateMatchNotification, UUID> {
    boolean existsByJobIdAndSeekerId(UUID jobId, UUID seekerId);

    Optional<CandidateMatchNotification> findByJobIdAndSeekerId(UUID jobId, UUID seekerId);

    List<CandidateMatchNotification> findAllByJobIdAndSeekerIdIn(UUID jobId, Collection<UUID> seekerIds);

    Optional<CandidateMatchNotification> findByIdAndSeekerId(UUID id, UUID seekerId);

    List<CandidateMatchNotification> findAllBySeekerIdOrderByCreatedAtDesc(UUID seekerId);
}
