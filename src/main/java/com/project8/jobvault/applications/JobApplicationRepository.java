package com.project8.jobvault.applications;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Optional<JobApplication> findByJobIdAndSeekerId(UUID jobId, UUID seekerId);

    Optional<JobApplication> findByIdAndJobEmployerId(UUID id, UUID employerId);

    Optional<JobApplication> findByIdAndSeekerId(UUID id, UUID seekerId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobApplication a
            set a.status = com.project8.jobvault.applications.ApplicationStatus.SUBMITTED,
            a.submittedAt = :submittedAt
            where a.job.id = :jobId
              and a.seeker.id = :seekerId
              and a.status = com.project8.jobvault.applications.ApplicationStatus.DRAFT
            """)
    int transitionDraftToSubmitted(
            @Param("jobId") UUID jobId,
            @Param("seekerId") UUID seekerId,
            @Param("submittedAt") Instant submittedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobApplication a
            set a.status = :newStatus,
            a.reviewedAt = coalesce(:reviewedAt, a.reviewedAt),
            a.decidedAt = coalesce(:decidedAt, a.decidedAt)
            where a.id = :applicationId
              and a.seeker.id = :seekerId
              and a.status = :expectedStatus
            """)
    int transitionForSeeker(
            @Param("applicationId") UUID applicationId,
            @Param("seekerId") UUID seekerId,
            @Param("expectedStatus") ApplicationStatus expectedStatus,
            @Param("newStatus") ApplicationStatus newStatus,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("decidedAt") Instant decidedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobApplication a
            set a.status = :newStatus,
            a.reviewedAt = coalesce(:reviewedAt, a.reviewedAt),
            a.decidedAt = coalesce(:decidedAt, a.decidedAt)
            where a.id = :applicationId
              and a.job.employer.id = :employerId
              and a.status = :expectedStatus
            """)
    int transitionForEmployer(
            @Param("applicationId") UUID applicationId,
            @Param("employerId") UUID employerId,
            @Param("expectedStatus") ApplicationStatus expectedStatus,
            @Param("newStatus") ApplicationStatus newStatus,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("decidedAt") Instant decidedAt);
}
