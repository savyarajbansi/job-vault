package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchingFacade {
    private static final double COSINE_WEIGHT = 0.8;
    private static final double SKILL_WEIGHT = 0.2;

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider;
    private final TextTokenizer textTokenizer = new TextTokenizer(Set.of(
            "the", "a", "an", "and", "or", "for", "to", "of", "in", "on", "with"));

    public MatchingFacade(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.resumeMetadataRepositoryProvider = resumeMetadataRepositoryProvider;
    }

    public SeekerJobMatchResponse seekerMatches(UUID seekerId, int limit, int offset) {
        ResumeMetadata resume = resumeMetadataRepository()
                .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                        seekerId,
                        ResumeProcessingStatus.PARSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));

        List<Job> activeJobs = jobRepository().findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE);
        List<ScoredJob> scored = new ArrayList<>();
        for (Job job : activeJobs) {
            ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job);
            scored.add(new ScoredJob(job, breakdown));
        }
        scored.sort((left, right) -> Double.compare(right.breakdown.overallScore(), left.breakdown.overallScore()));

        int total = scored.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        int end = Math.min(total, safeOffset + safeLimit);
        List<SeekerJobMatchResponse.SeekerJobMatchResponseItem> items = new ArrayList<>();
        if (safeOffset < end) {
            for (ScoredJob item : scored.subList(safeOffset, end)) {
                items.add(new SeekerJobMatchResponse.SeekerJobMatchResponseItem(
                        item.job.getId(),
                        item.breakdown.overallScore(),
                        item.breakdown.factors(),
                        new SeekerJobMatchResponse.JobInfo(item.job.getTitle(), false),
                        item.breakdown.missingSkills()));
            }
        }
        return new SeekerJobMatchResponse(items, new MatchPage(safeLimit, safeOffset, total));
    }

    public EmployerCandidateMatchResponse employerCandidates(UUID employerId, UUID jobId, int limit, int offset) {
        Job job = jobRepository().findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getEmployer() == null || !Objects.equals(job.getEmployer().getId(), employerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not ACTIVE");
        }

        List<ResumeMetadata> parsedResumes = resumeMetadataRepository()
                .findAllByProcessingStatusOrderByParsedAtDesc(ResumeProcessingStatus.PARSED);
        List<ScoredResume> scored = new ArrayList<>();
        for (ResumeMetadata resume : parsedResumes) {
            ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job);
            scored.add(new ScoredResume(resume, breakdown));
        }
        scored.sort((left, right) -> Double.compare(right.breakdown.overallScore(), left.breakdown.overallScore()));

        int total = scored.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        int end = Math.min(total, safeOffset + safeLimit);
        List<EmployerCandidateMatchResponse.EmployerCandidateMatchItem> items = new ArrayList<>();
        if (safeOffset < end) {
            for (ScoredResume item : scored.subList(safeOffset, end)) {
                UserAccount seeker = item.resume.getSeeker();
                UUID seekerId = seeker == null ? null : seeker.getId();
                if (seekerId == null) {
                    continue;
                }
                items.add(new EmployerCandidateMatchResponse.EmployerCandidateMatchItem(
                        item.resume.getId(),
                        seekerId,
                        item.breakdown.overallScore(),
                        item.breakdown.factors(),
                        item.breakdown.missingSkills()));
            }
        }
        return new EmployerCandidateMatchResponse(items, new MatchPage(safeLimit, safeOffset, total));
    }

    public SkillGapResponse seekerSkillGap(UUID seekerId, UUID jobId) {
        ResumeMetadata resume = resumeMetadataRepository()
                .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                        seekerId,
                        ResumeProcessingStatus.PARSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        Job job = jobRepository().findByIdAndStatus(jobId, JobStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job);
        return new SkillGapResponse(jobId, breakdown.missingSkills());
    }

    private ScoredBreakdown scoreResumeAgainstJob(ResumeMetadata resume, Job job) {
        List<String> resumeTokens = textTokenizer.tokenize(orEmpty(resume.getParsedText()));
        List<String> jobTokens = textTokenizer.tokenize(orEmpty(job.getDescription()));
        List<List<String>> corpus = List.of(resumeTokens, jobTokens);
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(InverseDocumentFrequency.compute(corpus));
        double cosineScore = CosineSimilarity.compute(vectorizer.vectorize(resumeTokens), vectorizer.vectorize(jobTokens));

        Set<Skill> requiredSkills = job.getRequiredSkills() == null ? Set.of() : job.getRequiredSkills();
        Set<String> requiredSkillNames = requiredSkills.stream()
                .map(Skill::getName)
                .map(this::normalizeSkill)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> resumeSkills = splitSkills(resume.getInferredSkills());
        int overlapCount = 0;
        for (String required : requiredSkillNames) {
            if (resumeSkills.contains(required)) {
                overlapCount++;
            }
        }
        double skillsOverlap = requiredSkillNames.isEmpty() ? 0.0 : (double) overlapCount / requiredSkillNames.size();
        List<String> missingSkills = requiredSkillNames.stream()
                .filter(required -> !resumeSkills.contains(required))
                .sorted()
                .toList();

        double overall = clamp01(cosineScore * COSINE_WEIGHT + skillsOverlap * SKILL_WEIGHT);
        return new ScoredBreakdown(
                overall,
                // Experience/location remain 0.0 by design until PRD weighting/rules are finalized.
                new MatchFactorBreakdown(cosineScore, skillsOverlap, 0.0, 0.0),
                missingSkills);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private Set<String> splitSkills(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(this::normalizeSkill)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeSkill(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private record ScoredBreakdown(
            double overallScore,
            MatchFactorBreakdown factors,
            List<String> missingSkills) {
    }

    private record ScoredJob(
            Job job,
            ScoredBreakdown breakdown) {
    }

    private record ScoredResume(
            ResumeMetadata resume,
            ScoredBreakdown breakdown) {
    }

    private JobRepository jobRepository() {
        JobRepository repository = jobRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching service unavailable");
        }
        return repository;
    }

    private ResumeMetadataRepository resumeMetadataRepository() {
        ResumeMetadataRepository repository = resumeMetadataRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching service unavailable");
        }
        return repository;
    }
}
