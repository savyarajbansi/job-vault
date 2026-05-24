package com.project8.jobvault.parsing;

import com.project8.jobvault.resumes.ResumeMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "resume_parse_attempts")
public class ResumeParseAttempt {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeMetadata resume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResumeParseAttemptStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "extracted_text_length")
    private Integer extractedTextLength;

    @Column(name = "inferred_skill_count")
    private Integer inferredSkillCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ResumeParseAttempt() {
    }

    public UUID getId() {
        return id;
    }

    public ResumeMetadata getResume() {
        return resume;
    }

    public void setResume(ResumeMetadata resume) {
        this.resume = resume;
    }

    public ResumeParseAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(ResumeParseAttemptStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getExtractedTextLength() {
        return extractedTextLength;
    }

    public void setExtractedTextLength(Integer extractedTextLength) {
        this.extractedTextLength = extractedTextLength;
    }

    public Integer getInferredSkillCount() {
        return inferredSkillCount;
    }

    public void setInferredSkillCount(Integer inferredSkillCount) {
        this.inferredSkillCount = inferredSkillCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
