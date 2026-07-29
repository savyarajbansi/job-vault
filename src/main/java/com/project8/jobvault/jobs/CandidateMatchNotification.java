package com.project8.jobvault.jobs;

import com.project8.jobvault.users.UserAccount;
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
@Table(name = "candidate_match_notifications")
public class CandidateMatchNotification {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seeker_id", nullable = false)
    private UserAccount seeker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private UserAccount employer;

    @Column(name = "score", nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateMatchStatus status = CandidateMatchStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CandidateMatchNotification() {
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

    public UserAccount getSeeker() {
        return seeker;
    }

    public void setSeeker(UserAccount seeker) {
        this.seeker = seeker;
    }

    public UserAccount getEmployer() {
        return employer;
    }

    public void setEmployer(UserAccount employer) {
        this.employer = employer;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public CandidateMatchStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateMatchStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
