package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchingFacade {
    public static final String ALGORITHM_VERSION = "matching-v2";
    private static final int CACHE_BATCH_SIZE = 500;
    private static final String SKILL_SEPARATOR = "\u001f";

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider;
    private final ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider;
    private final ObjectProvider<MatchResultRepository> matchResultRepositoryProvider;
    private final CorpusIdfService corpusIdfService;
    private final MatchScorer matchScorer;

    public MatchingFacade(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider,
            ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider,
            ObjectProvider<MatchResultRepository> matchResultRepositoryProvider,
            CorpusIdfService corpusIdfService,
            MatchScorer matchScorer) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.resumeMetadataRepositoryProvider = resumeMetadataRepositoryProvider;
        this.matchAttemptRepositoryProvider = matchAttemptRepositoryProvider;
        this.matchResultRepositoryProvider = matchResultRepositoryProvider;
        this.corpusIdfService = corpusIdfService;
        this.matchScorer = matchScorer;
    }

    public SeekerJobMatchResponse seekerMatches(UserAccount seeker, int limit, int offset) {
        ResumeMetadata resume = null;
        long startNanos = System.nanoTime();
        try {
            resume = resumeMetadataRepository()
                    .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                            seeker.getId(), ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));

            MatchResultRepository cache = matchResultRepository();
            if (cache != null && hasRevision(resume.getUpdatedAt()) && hasRevision(seeker.getUpdatedAt())) {
                ensureResumeCache(resume, seeker, cache);
                Page<MatchResult> page = cache.findValidForResume(
                        resume.getId(),
                        resume.getUpdatedAt(),
                        seeker.getUpdatedAt(),
                        ALGORITHM_VERSION,
                        corpusIdfService.getSnapshot().fingerprint(),
                        new OffsetPageRequest(offset, limit));
                if (page != null) {
                    List<SeekerJobMatchResponse.SeekerJobMatchResponseItem> items = page.getContent().stream()
                            .map(this::toSeekerItem)
                            .toList();
                    SeekerJobMatchResponse response = new SeekerJobMatchResponse(
                            items, new MatchPage(limit, offset, toInt(page.getTotalElements())));
                    recordMatchAttempt(null, resume, MatchAttemptStatus.SUCCESS, null,
                            startNanos, toInt(page.getTotalElements()));
                    return response;
                }
            }

            int maxResults = offset + limit;
            PriorityQueue<ScoredJob> topMatches = new PriorityQueue<>(maxResults, this::compareWorstJobFirst);
            Page<Job> jobPage;
            int pageNumber = 0;
            do {
                jobPage = jobRepository().findAllByStatus(
                        JobStatus.ACTIVE, PageRequest.of(pageNumber++, CACHE_BATCH_SIZE));
                if (jobPage == null) {
                    break;
                }
                for (Job job : jobPage.getContent()) {
                    topMatches.offer(new ScoredJob(job, scoreResumeAgainstJob(resume, job, seeker)));
                    if (topMatches.size() > maxResults) {
                        topMatches.poll();
                    }
                }
            } while (jobPage.hasNext());
            List<ScoredJob> scored = topMatches.stream().sorted(this::compareBestJobFirst).toList();
            long total = jobRepository().countByStatus(JobStatus.ACTIVE);
            int end = Math.min(scored.size(), maxResults);
            List<SeekerJobMatchResponse.SeekerJobMatchResponseItem> items = end <= offset
                    ? List.of()
                    : scored.subList(Math.min(offset, scored.size()), end).stream()
                            .map(item -> new SeekerJobMatchResponse.SeekerJobMatchResponseItem(
                                    item.job().getId(),
                                    item.breakdown().overallScore(),
                                    item.breakdown().factors(),
                                    toJobInfo(item.job()),
                                    item.breakdown().missingSkills()))
                            .toList();
            SeekerJobMatchResponse response = new SeekerJobMatchResponse(
                    items, new MatchPage(limit, offset, toInt(total)));
            recordMatchAttempt(null, resume, MatchAttemptStatus.SUCCESS, null,
                    startNanos, toInt(total));
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

            MatchResultRepository cache = matchResultRepository();
            if (cache != null && hasRevision(job.getUpdatedAt())) {
                ensureJobCache(job, cache);
                Page<MatchResult> page = cache.findValidForJob(
                        job.getId(),
                        job.getUpdatedAt(),
                        ALGORITHM_VERSION,
                        corpusIdfService.getSnapshot().fingerprint(),
                        new OffsetPageRequest(offset, limit));
                if (page != null) {
                    List<EmployerCandidateMatchResponse.EmployerCandidateMatchItem> items = page.getContent().stream()
                            .map(this::toEmployerItem)
                            .toList();
                    EmployerCandidateMatchResponse response = new EmployerCandidateMatchResponse(
                            items, new MatchPage(limit, offset, toInt(page.getTotalElements())));
                    recordMatchAttempt(job, null, MatchAttemptStatus.SUCCESS, null,
                            startNanos, toInt(page.getTotalElements()));
                    return response;
                }
            }

            int maxResults = offset + limit;
            PriorityQueue<ScoredResume> topMatches = new PriorityQueue<>(maxResults, this::compareWorstResumeFirst);
            Page<ResumeMetadata> resumePage;
            int pageNumber = 0;
            do {
                resumePage = resumeMetadataRepository().findParsedEnabled(
                        ResumeProcessingStatus.PARSED, PageRequest.of(pageNumber++, CACHE_BATCH_SIZE));
                if (resumePage == null) {
                    break;
                }
                for (ResumeMetadata resume : resumePage.getContent()) {
                    UserAccount candidate = resume.getSeeker();
                    if (candidate == null || !candidate.isEnabled()) {
                        continue;
                    }
                    topMatches.offer(new ScoredResume(resume, scoreResumeAgainstJob(resume, job, candidate)));
                    if (topMatches.size() > maxResults) {
                        topMatches.poll();
                    }
                }
            } while (resumePage.hasNext());
            List<ScoredResume> scored = topMatches.stream().sorted(this::compareBestResumeFirst).toList();
            long total = resumeMetadataRepository().countByProcessingStatusAndSeekerEnabled(
                    ResumeProcessingStatus.PARSED, true);
            int end = Math.min(scored.size(), maxResults);
            List<EmployerCandidateMatchResponse.EmployerCandidateMatchItem> items = end <= offset
                    ? List.of()
                    : scored.subList(Math.min(offset, scored.size()), end).stream()
                            .map(item -> toEmployerItem(item.resume(), item.breakdown())).toList();
            EmployerCandidateMatchResponse response = new EmployerCandidateMatchResponse(
                    items, new MatchPage(limit, offset, toInt(total)));
            recordMatchAttempt(job, null, MatchAttemptStatus.SUCCESS, null,
                    startNanos, toInt(total));
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
                            seeker.getId(), ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
            job = jobRepository().findByIdAndStatus(jobId, JobStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
            ScoredMatch breakdown = scoreResumeAgainstJob(resume, job, seeker);
            SkillGapResponse response = new SkillGapResponse(jobId, breakdown.missingSkills());
            recordMatchAttempt(job, resume, MatchAttemptStatus.SUCCESS, null, startNanos,
                    breakdown.missingSkills().size());
            return response;
        } catch (ResponseStatusException ex) {
            recordMatchAttempt(job, resume, MatchAttemptStatus.FAILED, "ERR_MATCH_002", startNanos, null);
            throw ex;
        }
    }

    public ScoredMatch scoreResumeAgainstJob(ResumeMetadata resume, Job job, UserAccount seeker) {
        return matchScorer.score(resume, job, seeker);
    }

    @Transactional(readOnly = true)
    public ScoredMatch scoreCurrentCandidate(UUID jobId, UUID seekerId) {
        Job job = jobRepository().findByIdAndStatus(jobId, JobStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        ResumeMetadata resume = resumeMetadataRepository()
                .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                        seekerId, ResumeProcessingStatus.PARSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        UserAccount seeker = resume.getSeeker();
        if (seeker == null || !Objects.equals(seeker.getId(), seekerId) || !seeker.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seeker not found");
        }
        return scoreResumeAgainstJob(resume, job, seeker);
    }

    @Transactional
    void ensureResumeCache(ResumeMetadata resume, UserAccount seeker, MatchResultRepository cache) {
        CorpusIdfService.CorpusSnapshot snapshot = corpusIdfService.getSnapshot();
        long expected = jobRepository().countByStatus(JobStatus.ACTIVE);
        long cached = cache.countValidForResume(
                resume.getId(), resume.getUpdatedAt(), seeker.getUpdatedAt(),
                ALGORITHM_VERSION, snapshot.fingerprint());
        if (cached == expected) {
            return;
        }

        int pageNumber = 0;
        Page<Job> page;
        do {
            page = jobRepository().findAllByStatus(JobStatus.ACTIVE,
                    PageRequest.of(pageNumber++, CACHE_BATCH_SIZE));
            if (page == null) {
                return;
            }
            for (Job job : page.getContent()) {
                saveMatchResult(cache, job, resume, seeker, scoreResumeAgainstJob(resume, job, seeker), snapshot);
            }
        } while (page.hasNext());
    }

    @Transactional
    void ensureJobCache(Job job, MatchResultRepository cache) {
        CorpusIdfService.CorpusSnapshot snapshot = corpusIdfService.getSnapshot();
        long expected = resumeMetadataRepository().countByProcessingStatusAndSeekerEnabled(
                ResumeProcessingStatus.PARSED, true);
        long cached = cache.countValidForJob(
                job.getId(), job.getUpdatedAt(), ALGORITHM_VERSION, snapshot.fingerprint());
        if (cached == expected) {
            return;
        }

        int pageNumber = 0;
        Page<ResumeMetadata> page;
        do {
            page = resumeMetadataRepository().findParsedEnabled(
                    ResumeProcessingStatus.PARSED, PageRequest.of(pageNumber++, CACHE_BATCH_SIZE));
            if (page == null) {
                return;
            }
            for (ResumeMetadata resume : page.getContent()) {
                UserAccount seeker = resume.getSeeker();
                saveMatchResult(cache, job, resume, seeker, scoreResumeAgainstJob(resume, job, seeker), snapshot);
            }
        } while (page.hasNext());
    }

    private void saveMatchResult(
            MatchResultRepository cache,
            Job job,
            ResumeMetadata resume,
            UserAccount seeker,
            ScoredMatch scored,
            CorpusIdfService.CorpusSnapshot snapshot) {
        MatchResult result = cache.findByJobIdAndResumeId(job.getId(), resume.getId())
                .orElseGet(MatchResult::new);
        result.setJob(job);
        result.setResume(resume);
        result.setOverallScore(scored.overallScore());
        result.setCosineScore(scored.factors().cosine());
        result.setSkillsScore(scored.factors().skillsOverlap());
        result.setExperienceScore(scored.factors().experience());
        result.setLocationScore(scored.factors().location());
        result.setCosineAvailable(scored.factors().cosineAvailable());
        result.setSkillsAvailable(scored.factors().skillsAvailable());
        result.setExperienceAvailable(scored.factors().experienceAvailable());
        result.setLocationAvailable(scored.factors().locationAvailable());
        result.setMissingSkills(String.join(SKILL_SEPARATOR, scored.missingSkills()));
        result.setAlgorithmVersion(ALGORITHM_VERSION);
        result.setCorpusFingerprint(snapshot.fingerprint());
        result.setJobRevision(job.getUpdatedAt());
        result.setResumeRevision(resume.getUpdatedAt());
        result.setSeekerRevision(seeker == null ? null : seeker.getUpdatedAt());
        cache.save(result);
    }

    private SeekerJobMatchResponse.SeekerJobMatchResponseItem toSeekerItem(MatchResult result) {
        ScoredMatch scored = fromResult(result);
        return new SeekerJobMatchResponse.SeekerJobMatchResponseItem(
                result.getJob().getId(),
                scored.overallScore(),
                scored.factors(),
                toJobInfo(result.getJob()),
                scored.missingSkills());
    }

    private EmployerCandidateMatchResponse.EmployerCandidateMatchItem toEmployerItem(MatchResult result) {
        ScoredMatch scored = fromResult(result);
        UserAccount seeker = result.getResume().getSeeker();
        UUID seekerId = seeker == null ? null : seeker.getId();
        String displayName = seeker == null || seeker.getDisplayName() == null
                || seeker.getDisplayName().isBlank() ? null : seeker.getDisplayName();
        return new EmployerCandidateMatchResponse.EmployerCandidateMatchItem(
                result.getResume().getId(), seekerId, displayName,
                scored.overallScore(), scored.factors(), scored.missingSkills());
    }

    private EmployerCandidateMatchResponse.EmployerCandidateMatchItem toEmployerItem(
            ResumeMetadata resume, ScoredMatch scored) {
        UserAccount seeker = resume.getSeeker();
        UUID seekerId = seeker == null ? null : seeker.getId();
        String displayName = seeker == null || seeker.getDisplayName() == null || seeker.getDisplayName().isBlank()
                ? null
                : seeker.getDisplayName();
        return new EmployerCandidateMatchResponse.EmployerCandidateMatchItem(
                resume.getId(), seekerId, displayName, scored.overallScore(), scored.factors(), scored.missingSkills());
    }

    private ScoredMatch fromResult(MatchResult result) {
        MatchFactorBreakdown factors = new MatchFactorBreakdown(
                result.getCosineScore(), result.getSkillsScore(), result.getExperienceScore(),
                result.getLocationScore(), result.isCosineAvailable(), result.isSkillsAvailable(),
                result.isExperienceAvailable(), result.isLocationAvailable());
        List<String> missingSkills = result.getMissingSkills() == null || result.getMissingSkills().isBlank()
                ? List.of()
                : List.of(result.getMissingSkills().split(SKILL_SEPARATOR));
        return new ScoredMatch(result.getOverallScore(), factors, missingSkills);
    }

    private SeekerJobMatchResponse.JobInfo toJobInfo(Job job) {
        List<String> requiredSkills = job.getRequiredSkills() == null
                ? List.of()
                : job.getRequiredSkills().stream()
                        .map(Skill::getName)
                        .filter(Objects::nonNull)
                        .map(matchScorer::canonicalizeSkill)
                        .sorted()
                        .toList();
        return new SeekerJobMatchResponse.JobInfo(
                job.getTitle(), job.getCompanyName(), job.getLocation(), job.getRemoteEligible(),
                job.getSalaryMin(), job.getSalaryMax(), job.getEducationRequirement(), requiredSkills);
    }

    private void sortJobs(List<ScoredJob> scored) {
        scored.sort(this::compareBestJobFirst);
    }

    private void sortResumes(List<ScoredResume> scored) {
        scored.sort(this::compareBestResumeFirst);
    }

    private int compareBestJobFirst(ScoredJob left, ScoredJob right) {
        return Comparator.comparingDouble((ScoredJob item) -> item.breakdown().overallScore()).reversed()
                .thenComparing(item -> item.job().getId())
                .compare(left, right);
    }

    private int compareWorstJobFirst(ScoredJob left, ScoredJob right) {
        return compareBestJobFirst(right, left);
    }

    private int compareBestResumeFirst(ScoredResume left, ScoredResume right) {
        return Comparator.comparingDouble((ScoredResume item) -> item.breakdown().overallScore()).reversed()
                .thenComparing(item -> item.resume().getId())
                .compare(left, right);
    }

    private int compareWorstResumeFirst(ScoredResume left, ScoredResume right) {
        return compareBestResumeFirst(right, left);
    }

    private boolean hasRevision(java.time.Instant revision) {
        return revision != null;
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private void recordMatchAttempt(
            Job job, ResumeMetadata resume, MatchAttemptStatus status, String errorCode,
            long startNanos, Integer resultCount) {
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

    private MatchResultRepository matchResultRepository() {
        return matchResultRepositoryProvider.getIfAvailable();
    }

    private record ScoredJob(Job job, ScoredMatch breakdown) {
    }

    private record ScoredResume(ResumeMetadata resume, ScoredMatch breakdown) {
    }
}
