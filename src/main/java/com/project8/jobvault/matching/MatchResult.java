package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.resumes.ResumeMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "match_results")
public class MatchResult {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeMetadata resume;

    @Column(name = "overall_score", nullable = false)
    private double overallScore;

    @Column(name = "cosine_score", nullable = false)
    private double cosineScore;

    @Column(name = "skills_score", nullable = false)
    private double skillsScore;

    @Column(name = "experience_score", nullable = false)
    private double experienceScore;

    @Column(name = "location_score", nullable = false)
    private double locationScore;

    @Column(name = "sector_score", nullable = false)
    private double sectorScore;

    @Column(name = "cosine_available", nullable = false)
    private boolean cosineAvailable;

    @Column(name = "skills_available", nullable = false)
    private boolean skillsAvailable;

    @Column(name = "experience_available", nullable = false)
    private boolean experienceAvailable;

    @Column(name = "location_available", nullable = false)
    private boolean locationAvailable;

    @Column(name = "sector_available", nullable = false)
    private boolean sectorAvailable;

    @Column(name = "missing_skills", nullable = false, columnDefinition = "text")
    private String missingSkills = "";

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    @Column(name = "corpus_fingerprint", nullable = false, length = 128)
    private String corpusFingerprint;

    @Column(name = "job_revision")
    private Instant jobRevision;

    @Column(name = "resume_revision")
    private Instant resumeRevision;

    @Column(name = "seeker_revision")
    private Instant seekerRevision;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected MatchResult() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public ResumeMetadata getResume() {
        return resume;
    }

    public void setResume(ResumeMetadata resume) {
        this.resume = resume;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public double getCosineScore() {
        return cosineScore;
    }

    public void setCosineScore(double cosineScore) {
        this.cosineScore = cosineScore;
    }

    public double getSkillsScore() {
        return skillsScore;
    }

    public void setSkillsScore(double skillsScore) {
        this.skillsScore = skillsScore;
    }

    public double getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(double experienceScore) {
        this.experienceScore = experienceScore;
    }

    public double getLocationScore() {
        return locationScore;
    }

    public void setLocationScore(double locationScore) {
        this.locationScore = locationScore;
    }

    public double getSectorScore() {
        return sectorScore;
    }

    public void setSectorScore(double sectorScore) {
        this.sectorScore = sectorScore;
    }

    public boolean isCosineAvailable() {
        return cosineAvailable;
    }

    public void setCosineAvailable(boolean cosineAvailable) {
        this.cosineAvailable = cosineAvailable;
    }

    public boolean isSkillsAvailable() {
        return skillsAvailable;
    }

    public void setSkillsAvailable(boolean skillsAvailable) {
        this.skillsAvailable = skillsAvailable;
    }

    public boolean isExperienceAvailable() {
        return experienceAvailable;
    }

    public void setExperienceAvailable(boolean experienceAvailable) {
        this.experienceAvailable = experienceAvailable;
    }

    public boolean isLocationAvailable() {
        return locationAvailable;
    }

    public void setLocationAvailable(boolean locationAvailable) {
        this.locationAvailable = locationAvailable;
    }

    public boolean isSectorAvailable() {
        return sectorAvailable;
    }

    public void setSectorAvailable(boolean sectorAvailable) {
        this.sectorAvailable = sectorAvailable;
    }

    public String getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(String missingSkills) {
        this.missingSkills = missingSkills == null ? "" : missingSkills;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public String getCorpusFingerprint() {
        return corpusFingerprint;
    }

    public void setCorpusFingerprint(String corpusFingerprint) {
        this.corpusFingerprint = corpusFingerprint;
    }

    public Instant getJobRevision() {
        return jobRevision;
    }

    public void setJobRevision(Instant jobRevision) {
        this.jobRevision = jobRevision;
    }

    public Instant getResumeRevision() {
        return resumeRevision;
    }

    public void setResumeRevision(Instant resumeRevision) {
        this.resumeRevision = resumeRevision;
    }

    public Instant getSeekerRevision() {
        return seekerRevision;
    }

    public void setSeekerRevision(Instant seekerRevision) {
        this.seekerRevision = seekerRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
