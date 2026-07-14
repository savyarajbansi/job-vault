package com.project8.jobvault.jobs;

import com.project8.jobvault.users.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JobRepository extends JpaRepository<Job, UUID> {

    // ── Public / match queries ─────────────────────────────────────────────────

    /** Unbounded list used only for corpus building (all descriptions needed). */
    List<Job> findAllByStatusOrderByCreatedAtDesc(JobStatus status);

    /**
     * Paginated active-job query for use in matching facades.
     * Callers should pass a {@link Pageable} with a reasonable page size
     * (e.g. 500) so that memory pressure is bounded as the job catalogue grows.
     */
    Page<Job> findAllByStatus(JobStatus status, Pageable pageable);

    // ── Employer queries ───────────────────────────────────────────────────────

    List<Job> findAllByEmployerIdOrderByCreatedAtDesc(UUID employerId);

    Optional<Job> findByIdAndStatus(UUID id, JobStatus status);

    // ── Admin moderation mutations ─────────────────────────────────────────────

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = com.project8.jobvault.jobs.JobStatus.ACTIVE,
                j.moderationAction = com.project8.jobvault.jobs.JobModerationAction.APPROVED,
                j.moderationReason = null,
                j.moderatedBy = :moderatedBy,
                j.moderatedAt = :moderatedAt,
                j.publishedAt = :publishedAt,
                j.disabledAt = null
            where j.id = :jobId
              and (j.status = com.project8.jobvault.jobs.JobStatus.DRAFT
                or j.status = com.project8.jobvault.jobs.JobStatus.DISABLED)
            """)
    int approveForAdmin(
            @Param("jobId") UUID jobId,
            @Param("moderatedBy") UserAccount moderatedBy,
            @Param("moderatedAt") Instant moderatedAt,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = com.project8.jobvault.jobs.JobStatus.DISABLED,
                j.moderationAction = :moderationAction,
                j.moderationReason = :moderationReason,
                j.moderatedBy = :moderatedBy,
                j.moderatedAt = :moderatedAt,
                j.disabledAt = :disabledAt
            where j.id = :jobId
              and j.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            """)
    int moderateActiveToDisabled(
            @Param("jobId") UUID jobId,
            @Param("moderationAction") JobModerationAction moderationAction,
            @Param("moderationReason") String moderationReason,
            @Param("moderatedBy") UserAccount moderatedBy,
            @Param("moderatedAt") Instant moderatedAt,
            @Param("disabledAt") Instant disabledAt);

    // ── Employer lifecycle mutations ───────────────────────────────────────────

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = com.project8.jobvault.jobs.JobStatus.ACTIVE,
            j.publishedAt = :publishedAt,
            j.disabledAt = null
            where j.id = :jobId
              and j.employer.id = :employerId
              and j.status = com.project8.jobvault.jobs.JobStatus.DRAFT
            """)
    int transitionDraftToActive(
            @Param("jobId") UUID jobId,
            @Param("employerId") UUID employerId,
            @Param("publishedAt") Instant publishedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = com.project8.jobvault.jobs.JobStatus.DISABLED,
            j.disabledAt = :disabledAt
            where j.id = :jobId
              and j.employer.id = :employerId
              and j.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            """)
    int transitionActiveToDisabled(
            @Param("jobId") UUID jobId,
            @Param("employerId") UUID employerId,
            @Param("disabledAt") Instant disabledAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = com.project8.jobvault.jobs.JobStatus.ACTIVE,
            j.publishedAt = :publishedAt,
            j.disabledAt = null
            where j.id = :jobId
              and j.employer.id = :employerId
              and j.status = com.project8.jobvault.jobs.JobStatus.DISABLED
              and (
            j.moderationAction is null
            or j.moderationAction = com.project8.jobvault.jobs.JobModerationAction.APPROVED
              )
            """)
    int transitionDisabledToActive(
            @Param("jobId") UUID jobId,
            @Param("employerId") UUID employerId,
            @Param("publishedAt") Instant publishedAt);
}