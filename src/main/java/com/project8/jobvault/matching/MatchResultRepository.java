package com.project8.jobvault.matching;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {
    Optional<MatchResult> findByJobIdAndResumeId(UUID jobId, UUID resumeId);

    @Query(value = """
            select result
            from MatchResult result
            join fetch result.job job
            where result.resume.id = :resumeId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.resumeRevision = :resumeRevision
              and result.seekerRevision = :seekerRevision
              and result.jobRevision = job.updatedAt
              and job.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            order by result.overallScore desc, job.id asc
            """,
            countQuery = """
            select count(result)
            from MatchResult result
            join result.job job
            where result.resume.id = :resumeId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.resumeRevision = :resumeRevision
              and result.seekerRevision = :seekerRevision
              and result.jobRevision = job.updatedAt
              and job.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            """)
    Page<MatchResult> findValidForResume(
            @Param("resumeId") UUID resumeId,
            @Param("resumeRevision") Instant resumeRevision,
            @Param("seekerRevision") Instant seekerRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint,
            Pageable pageable);

    @Query("""
            select result
            from MatchResult result
            join fetch result.job job
            where result.resume.id = :resumeId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.resumeRevision = :resumeRevision
              and result.seekerRevision = :seekerRevision
              and result.jobRevision = job.updatedAt
              and job.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            order by result.overallScore desc, job.id asc
            """)
    List<MatchResult> findValidForResumeAll(
            @Param("resumeId") UUID resumeId,
            @Param("resumeRevision") Instant resumeRevision,
            @Param("seekerRevision") Instant seekerRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint);

    @Query(value = """
            select result
            from MatchResult result
            join fetch result.resume resume
            join fetch resume.seeker seeker
            where result.job.id = :jobId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.jobRevision = :jobRevision
              and result.resumeRevision = resume.updatedAt
              and result.seekerRevision = seeker.updatedAt
              and resume.processingStatus = com.project8.jobvault.resumes.ResumeProcessingStatus.PARSED
              and seeker.enabled = true
            order by result.overallScore desc, resume.id asc
            """,
            countQuery = """
            select count(result)
            from MatchResult result
            join result.resume resume
            join resume.seeker seeker
            where result.job.id = :jobId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.jobRevision = :jobRevision
              and result.resumeRevision = resume.updatedAt
              and result.seekerRevision = seeker.updatedAt
              and resume.processingStatus = com.project8.jobvault.resumes.ResumeProcessingStatus.PARSED
              and seeker.enabled = true
            """)
    Page<MatchResult> findValidForJob(
            @Param("jobId") UUID jobId,
            @Param("jobRevision") Instant jobRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint,
            Pageable pageable);

    @Query("""
            select result
            from MatchResult result
            join fetch result.resume resume
            join fetch resume.seeker seeker
            where result.job.id = :jobId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.jobRevision = :jobRevision
              and result.resumeRevision = resume.updatedAt
              and result.seekerRevision = seeker.updatedAt
              and resume.processingStatus = com.project8.jobvault.resumes.ResumeProcessingStatus.PARSED
              and seeker.enabled = true
            order by result.overallScore desc, resume.id asc
            """)
    List<MatchResult> findValidForJobAll(
            @Param("jobId") UUID jobId,
            @Param("jobRevision") Instant jobRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint);

    @Query("""
            select count(result)
            from MatchResult result
            join result.job job
            where result.resume.id = :resumeId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.resumeRevision = :resumeRevision
              and result.seekerRevision = :seekerRevision
              and result.jobRevision = job.updatedAt
              and job.status = com.project8.jobvault.jobs.JobStatus.ACTIVE
            """)
    long countValidForResume(
            @Param("resumeId") UUID resumeId,
            @Param("resumeRevision") Instant resumeRevision,
            @Param("seekerRevision") Instant seekerRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint);

    @Query("""
            select count(result)
            from MatchResult result
            join result.resume resume
            join resume.seeker seeker
            where result.job.id = :jobId
              and result.algorithmVersion = :algorithmVersion
              and result.corpusFingerprint = :corpusFingerprint
              and result.jobRevision = :jobRevision
              and result.resumeRevision = resume.updatedAt
              and result.seekerRevision = seeker.updatedAt
              and resume.processingStatus = com.project8.jobvault.resumes.ResumeProcessingStatus.PARSED
              and seeker.enabled = true
            """)
    long countValidForJob(
            @Param("jobId") UUID jobId,
            @Param("jobRevision") Instant jobRevision,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("corpusFingerprint") String corpusFingerprint);
}
