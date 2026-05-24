package com.project8.jobvault.resumes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeMetadataRepository extends JpaRepository<ResumeMetadata, UUID> {
    Optional<ResumeMetadata> findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
            UUID seekerId,
            ResumeProcessingStatus processingStatus);

    List<ResumeMetadata> findAllByProcessingStatusOrderByParsedAtDesc(ResumeProcessingStatus processingStatus);

    List<ResumeMetadata> findAllBySeekerIdOrderByCreatedAtDesc(UUID seekerId);
}
