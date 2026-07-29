package com.project8.jobvault.jobs;

import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.skills.SkillRepository;
import com.project8.jobvault.skills.TrendingSkillResponse;
import com.project8.jobvault.matching.MatchingPreferences;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class PublicJobController {
    private final JobRepository jobRepository;
    private final ObjectProvider<SkillRepository> skillRepositoryProvider;

    public PublicJobController(
            JobRepository jobRepository,
            ObjectProvider<SkillRepository> skillRepositoryProvider) {
        this.jobRepository = jobRepository;
        this.skillRepositoryProvider = skillRepositoryProvider;
    }

    @GetMapping
    public List<JobSummaryResponse> listPublished() {
        return jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE).stream()
                .map(job -> new JobSummaryResponse(
                        job.getId(),
                        job.getTitle(),
                        job.getCompanyName(),
                        MatchingPreferences.parseSectors(job.getSectorTags()),
                        job.getLocation(),
                        job.getWorkMode(),
                        job.getMinExperienceYears(),
                        job.getSalaryMin(),
                        job.getSalaryMax(),
                        job.getStatus(),
                        job.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobDetailResponse> getPublished(@PathVariable UUID jobId) {
        Optional<Job> job = jobRepository.findByIdAndStatus(jobId, JobStatus.ACTIVE);
        return job.map(value -> {
            List<String> requiredSkills = value.getRequiredSkills() == null
                    ? List.of()
                    : value.getRequiredSkills().stream()
                            .map(Skill::getName)
                            .filter(Objects::nonNull)
                            .sorted()
                            .toList();
            return ResponseEntity.ok(new JobDetailResponse(
                    value.getId(),
                    value.getTitle(),
                    value.getDescription(),
                    value.getCompanyName(),
                    MatchingPreferences.parseSectors(value.getSectorTags()),
                    value.getLocation(),
                    value.getWorkMode(),
                    value.getMinExperienceYears(),
                    value.getSalaryMin(),
                    value.getSalaryMax(),
                    value.getEducationRequirement(),
                    requiredSkills,
                    value.getStatus(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getPublishedAt(),
                    value.getDisabledAt()));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/trending-skills")
    public List<TrendingSkillResponse> trendingSkills() {
        SkillRepository skillRepository = skillRepositoryProvider.getIfAvailable();
        if (skillRepository == null) {
            return List.of();
        }
        return skillRepository.findTrendingSkills().stream()
                .map(row -> new TrendingSkillResponse(
                        row.getSkillId(),
                        row.getSkillName(),
                        row.getScore() == null ? 0.0 : row.getScore().doubleValue()))
                .toList();
    }
}
