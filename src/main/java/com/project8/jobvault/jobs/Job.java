package com.project8.jobvault.jobs;

import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.matching.WorkMode;
import com.project8.jobvault.users.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private UserAccount employer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "sector_tags", columnDefinition = "text")
    private String sectorTags;

    @Column(name = "location", length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 20)
    private WorkMode workMode;

    @Column(name = "min_experience_years")
    private Integer minExperienceYears;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_requirement", length = 50)
    private EducationRequirement educationRequirement;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_required_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> requiredSkills = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_action", length = 20)
    private JobModerationAction moderationAction;

    @Column(name = "moderation_reason", columnDefinition = "text")
    private String moderationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private UserAccount moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserAccount getEmployer() {
        return employer;
    }

    public void setEmployer(UserAccount employer) {
        this.employer = employer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getSectorTags() {
        return sectorTags;
    }

    public void setSectorTags(String sectorTags) {
        this.sectorTags = sectorTags;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
    }

    /** Compatibility aliases for older callers while the new job API rolls out. */
    @Transient
    @Deprecated
    public String getSector() {
        return sectorTags;
    }

    @Transient
    @Deprecated
    public void setSector(String sector) {
        this.sectorTags = sector;
    }

    @Transient
    @Deprecated
    public Boolean getRemoteEligible() {
        return workMode == null ? null : workMode != WorkMode.ON_SITE;
    }

    @Transient
    @Deprecated
    public void setRemoteEligible(Boolean remoteEligible) {
        this.workMode = remoteEligible == null ? null : remoteEligible ? WorkMode.REMOTE : WorkMode.ON_SITE;
    }

    public Integer getMinExperienceYears() {
        return minExperienceYears;
    }

    public void setMinExperienceYears(Integer minExperienceYears) {
        this.minExperienceYears = minExperienceYears;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Integer salaryMax) {
        this.salaryMax = salaryMax;
    }

    public EducationRequirement getEducationRequirement() {
        return educationRequirement;
    }

    public void setEducationRequirement(EducationRequirement educationRequirement) {
        this.educationRequirement = educationRequirement;
    }

    public Set<Skill> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Set<Skill> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public JobModerationAction getModerationAction() {
        return moderationAction;
    }

    public void setModerationAction(JobModerationAction moderationAction) {
        this.moderationAction = moderationAction;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }

    public UserAccount getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(UserAccount moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(Instant moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
