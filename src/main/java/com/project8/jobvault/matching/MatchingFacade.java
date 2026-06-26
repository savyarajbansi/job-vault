package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserDisplayNames;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final double COSINE_WEIGHT = 0.4;
    private static final double SKILL_WEIGHT = 0.3;
    private static final double EXPERIENCE_WEIGHT = 0.2;
    private static final double LOCATION_WEIGHT = 0.1;

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider;
    private final ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider;
    private final CorpusIdfService corpusIdfService;
    private final TextTokenizer textTokenizer = new TextTokenizer(MatchingStopwords.DEFAULT);

    public MatchingFacade(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider,
            ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider,
            CorpusIdfService corpusIdfService) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.resumeMetadataRepositoryProvider = resumeMetadataRepositoryProvider;
        this.matchAttemptRepositoryProvider = matchAttemptRepositoryProvider;
        this.corpusIdfService = corpusIdfService;
    }

    public SeekerJobMatchResponse seekerMatches(UserAccount seeker, int limit, int offset) {
        ResumeMetadata resume = null;
        long startNanos = System.nanoTime();
        try {
            resume = resumeMetadataRepository()
                    .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                            seeker.getId(),
                            ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));

            List<Job> activeJobs = jobRepository().findAllByStatusOrderByCreatedAtDesc(JobStatus.ACTIVE);
            List<ScoredJob> scored = new ArrayList<>();
            for (Job job : activeJobs) {
                ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job, seeker);
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
                            toJobInfo(item.job),
                            item.breakdown.missingSkills()));
                }
            }
            SeekerJobMatchResponse response = new SeekerJobMatchResponse(
                    items, new MatchPage(safeLimit, safeOffset, total));
            recordMatchAttempt(null, resume, MatchAttemptStatus.SUCCESS, null, startNanos, items.size());
            return response;
        } catch (ResponseStatusException ex) {
            recordMatchAttempt(null, resume, MatchAttemptStatus.FAILED, "ERR_MATCH_001", startNanos, null);
            throw ex;
        }
    }

    public EmployerCandidateMatchResponse employerCandidates(UUID employerId, UUID jobId, int limit, int offset) {
        Job job = null;
        long startNanos = System.nanoTime();
        try {
            job = jobRepository().findById(jobId)
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
                ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job, resume.getSeeker());
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
                    UserAccount seekerAccount = item.resume.getSeeker();
                    UUID seekerId = seekerAccount == null ? null : seekerAccount.getId();
                    if (seekerId == null) {
                        continue;
                    }
                    items.add(new EmployerCandidateMatchResponse.EmployerCandidateMatchItem(
                            item.resume.getId(),
                            seekerId,
                            UserDisplayNames.nameOrEmail(seekerAccount),
                            item.breakdown.overallScore(),
                            item.breakdown.factors(),
                            item.breakdown.missingSkills()));
                }
            }
            EmployerCandidateMatchResponse response = new EmployerCandidateMatchResponse(
                    items, new MatchPage(safeLimit, safeOffset, total));
            recordMatchAttempt(job, null, MatchAttemptStatus.SUCCESS, null, startNanos, items.size());
            return response;
        } catch (ResponseStatusException ex) {
            recordMatchAttempt(job, null, MatchAttemptStatus.FAILED, "ERR_MATCH_001", startNanos, null);
            throw ex;
        }
    }

    public SkillGapResponse seekerSkillGap(UserAccount seeker, UUID jobId) {
        ResumeMetadata resume = null;
        Job job = null;
        long startNanos = System.nanoTime();
        try {
            resume = resumeMetadataRepository()
                    .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                            seeker.getId(),
                            ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
            job = jobRepository().findByIdAndStatus(jobId, JobStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
            ScoredBreakdown breakdown = scoreResumeAgainstJob(resume, job, seeker);
            SkillGapResponse response = new SkillGapResponse(jobId, breakdown.missingSkills());
            recordMatchAttempt(job, resume, MatchAttemptStatus.SUCCESS, null, startNanos,
                    breakdown.missingSkills().size());
            return response;
        } catch (ResponseStatusException ex) {
            recordMatchAttempt(job, resume, MatchAttemptStatus.FAILED, "ERR_MATCH_002", startNanos, null);
            throw ex;
        }
    }

    // ── Scoring ────────────────────────────────────────────────────────────────

    private ScoredBreakdown scoreResumeAgainstJob(ResumeMetadata resume, Job job, UserAccount seeker) {
        List<String> resumeTokens = textTokenizer.tokenize(orEmpty(resume.getParsedText()));
        List<String> jobTokens = textTokenizer.tokenize(orEmpty(job.getDescription()));
        Map<String, Double> idf = corpusIdfService.getIdf();
        if (idf.isEmpty()) {
            corpusIdfService.rebuildFromRepository();
            idf = corpusIdfService.getIdf();
        }
        if (idf.isEmpty()) {
            List<List<String>> fallbackCorpus = List.of(resumeTokens, jobTokens);
            idf = InverseDocumentFrequency.compute(fallbackCorpus);
        }
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(idf);
        double cosineScore = CosineSimilarity.compute(
                vectorizer.vectorize(resumeTokens), vectorizer.vectorize(jobTokens));

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
        double skillsOverlap = requiredSkillNames.isEmpty()
                ? 0.0
                : (double) overlapCount / requiredSkillNames.size();
        List<String> missingSkills = requiredSkillNames.stream()
                .filter(required -> !resumeSkills.contains(required))
                .sorted()
                .toList();

        double experienceScore = experienceScore(seeker, job);
        double locationScore = locationScore(seeker, job);
        double overall = clamp01(
                cosineScore * COSINE_WEIGHT
                        + skillsOverlap * SKILL_WEIGHT
                        + experienceScore * EXPERIENCE_WEIGHT
                        + locationScore * LOCATION_WEIGHT);
        return new ScoredBreakdown(
                overall,
                new MatchFactorBreakdown(cosineScore, skillsOverlap, experienceScore, locationScore),
                missingSkills);
    }

    // ── JobInfo mapping ────────────────────────────────────────────────────────

    private SeekerJobMatchResponse.JobInfo toJobInfo(Job job) {
        List<String> requiredSkills = job.getRequiredSkills() == null
                ? List.of()
                : job.getRequiredSkills().stream()
                        .map(Skill::getName)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList();
        return new SeekerJobMatchResponse.JobInfo(
                job.getTitle(),
                job.getCompanyName(),
                job.getLocation(),
                job.getRemoteEligible(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getEducationRequirement(),
                requiredSkills);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

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

    private double experienceScore(UserAccount seeker, Job job) {
        Integer minRequired = job == null ? null : job.getMinExperienceYears();
        if (minRequired == null || minRequired <= 0) {
            return 1.0;
        }
        Integer seekerYears = seeker == null ? null : seeker.getYearsExperience();
        if (seekerYears == null || seekerYears <= 0) {
            return 0.0;
        }
        if (seekerYears >= minRequired) {
            return 1.0;
        }
        return clamp01((double) seekerYears / minRequired);
    }

    private double locationScore(UserAccount seeker, Job job) {
        boolean remoteEligible = job != null && Boolean.TRUE.equals(job.getRemoteEligible());
        boolean remoteOk = seeker != null && Boolean.TRUE.equals(seeker.getRemoteOk());
        String seekerLocation = normalizeLocation(seeker == null ? null : seeker.getPreferredLocation());
        String jobLocation = normalizeLocation(job == null ? null : job.getLocation());
        boolean matches = locationMatches(seekerLocation, jobLocation);

        if (remoteEligible && remoteOk) {
            return 1.0;
        }
        return matches ? 1.0 : 0.0;
    }

    private boolean locationMatches(String seekerLocation, String jobLocation) {
        if (seekerLocation == null || jobLocation == null) {
            return false;
        }
        if (seekerLocation.equals(jobLocation)) {
            return true;
        }
        return seekerLocation.contains(jobLocation) || jobLocation.contains(seekerLocation);
    }

    private String normalizeLocation(String location) {
        if (location == null) {
            return null;
        }
        String trimmed = location.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Audit ──────────────────────────────────────────────────────────────────

    private void recordMatchAttempt(
            Job job,
            ResumeMetadata resume,
            MatchAttemptStatus status,
            String errorCode,
            long startNanos,
            Integer resultCount) {
        MatchAttemptRepository repository = matchAttemptRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        MatchAttempt attempt = new MatchAttempt();
        attempt.setJob(job);
        attempt.setResume(resume);
        attempt.setStatus(status);
        attempt.setErrorCode(errorCode);
        attempt.setResultCount(resultCount);
        attempt.setDurationMs(toDurationMs(startNanos));
        repository.save(attempt);
    }

    private int toDurationMs(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long millis = Duration.ofNanos(Math.max(0L, elapsedNanos)).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    // ── Repository accessors ───────────────────────────────────────────────────

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

    // ── Inner records ──────────────────────────────────────────────────────────

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
}