package com.project8.jobvault.resumes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeMetadataRepository extends JpaRepository<ResumeMetadata, UUID> {
    Optional<ResumeMetadata> findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
            UUID seekerId,
            ResumeProcessingStatus processingStatus);

    List<ResumeMetadata> findAllByProcessingStatusOrderByParsedAtDesc(ResumeProcessingStatus processingStatus);

    @Query("""
            select resume
            from ResumeMetadata resume
            join fetch resume.seeker seeker
            where resume.processingStatus = :status
              and seeker.enabled = true
            order by resume.parsedAt desc, resume.id asc
            """)
    Page<ResumeMetadata> findParsedEnabled(
            @Param("status") ResumeProcessingStatus status,
            Pageable pageable);

    long countByProcessingStatusAndSeekerEnabled(ResumeProcessingStatus status, boolean enabled);

    List<ResumeMetadata> findAllBySeekerIdOrderByCreatedAtDesc(UUID seekerId);
}
