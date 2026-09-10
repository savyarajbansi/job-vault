package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.CandidateMatchNotificationRepository;
import com.project8.jobvault.jobs.CandidateMatchNotification;
import com.project8.jobvault.jobs.CandidateMatchStatus;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchingFacade {
    public static final String ALGORITHM_VERSION = "matching-v4";
    private static final int SCAN_BATCH_SIZE = 500;

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider;
    private final ObjectProvider<CandidateMatchNotificationRepository> shortlistRepositoryProvider;
    private final MatchScorer matchScorer;

    public MatchingFacade(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider,
            ObjectProvider<CandidateMatchNotificationRepository> shortlistRepositoryProvider,
            MatchScorer matchScorer) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.resumeMetadataRepositoryProvider = resumeMetadataRepositoryProvider;
        this.shortlistRepositoryProvider = shortlistRepositoryProvider;
        this.matchScorer = matchScorer;
    }

    @Transactional(readOnly = true)
    public SeekerJobMatchResponse seekerMatches(UserAccount seeker, int limit, int offset) {
        MatchPagination.validate(limit, offset);
        ResumeMetadata resume = resumeMetadataRepository()
                    .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                            seeker.getId(), ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        MatchScorer.ScoringContext scoringContext = matchScorer.newContext();
        MatchScorer.PreparedResume preparedResume = matchScorer.prepareResume(resume, scoringContext);

        int maxResults = offset + limit;
        PriorityQueue<ScoredJob> topMatches = new PriorityQueue<>(maxResults, this::compareWorstJobFirst);
        long eligibleTotal = 0;
        Page<Job> jobPage;
        int pageNumber = 0;
        do {
            jobPage = jobRepository().findAllByStatus(
                        JobStatus.ACTIVE,
                        PageRequest.of(pageNumber++, SCAN_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            if (jobPage == null) {
                break;
            }
            for (Job job : jobPage.getContent()) {
                if (!isEligible(seeker, job)) {
                    continue;
                }
                eligibleTotal++;
                topMatches.offer(new ScoredJob(job, scoreResumeAgainstJob(preparedResume, job, seeker)));
                if (topMatches.size() > maxResults) {
                    topMatches.poll();
                }
            }
        } while (jobPage.hasNext());
        List<ScoredJob> scored = topMatches.stream().sorted(this::compareBestJobFirst).toList();
        List<SeekerJobMatchResponse.SeekerJobMatchResponseItem> items = pageSlice(scored, offset, limit).stream()
                        .map(item -> new SeekerJobMatchResponse.SeekerJobMatchResponseItem(
                                item.job().getId(),
                                item.breakdown().overallScore(),
                                item.breakdown().factors(),
                                toJobInfo(item.job()),
                                item.breakdown().missingSkills()))
                        .toList();
        return new SeekerJobMatchResponse(items, new MatchPage(limit, offset, toInt(eligibleTotal)));
    }

    @Transactional(readOnly = true)
    public EmployerCandidateMatchResponse employerCandidates(UUID employerId, UUID jobId, int limit, int offset) {
        MatchPagination.validate(limit, offset);
        Job job = jobRepository().findById(jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getEmployer() == null || !Objects.equals(job.getEmployer().getId(), employerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not ACTIVE");
        }

        int maxResults = offset + limit;
        PriorityQueue<ScoredResume> topMatches = new PriorityQueue<>(maxResults, this::compareWorstResumeFirst);
        Set<UUID> seenSeekers = new HashSet<>();
        MatchScorer.ScoringContext scoringContext = matchScorer.newContext();
        Page<ResumeMetadata> resumePage;
        int pageNumber = 0;
        do {
            resumePage = resumeMetadataRepository().findParsedEnabled(
                        ResumeProcessingStatus.PARSED, PageRequest.of(pageNumber++, SCAN_BATCH_SIZE));
            if (resumePage == null) {
                break;
            }
            for (ResumeMetadata resume : resumePage.getContent()) {
                UserAccount candidate = resume.getSeeker();
                if (candidate == null || !candidate.isEnabled()) {
                    continue;
                }
                if (!isEligible(candidate, job)) {
                    continue;
                }
                if (!seenSeekers.add(candidate.getId())) {
                    continue;
                }
                MatchScorer.PreparedResume preparedResume = matchScorer.prepareResume(resume, scoringContext);
                topMatches.offer(new ScoredResume(
                        resume, scoreResumeAgainstJob(preparedResume, job, candidate)));
                if (topMatches.size() > maxResults) {
                    topMatches.poll();
                }
            }
        } while (resumePage.hasNext());
        List<ScoredResume> scored = topMatches.stream().sorted(this::compareBestResumeFirst).toList();
        List<ScoredResume> page = pageSlice(scored, offset, limit);
        Map<UUID, CandidateMatchStatus> shortlistStatuses = shortlistStatuses(job.getId(), page);
        List<EmployerCandidateMatchResponse.EmployerCandidateMatchItem> items = page.stream()
                        .map(item -> toEmployerItem(
                                item.resume(), item.breakdown(), shortlistStatuses.get(seekerId(item.resume()))))
                        .toList();
        return new EmployerCandidateMatchResponse(
                items, new MatchPage(limit, offset, toInt(seenSeekers.size())));
    }

    @Transactional(readOnly = true)
    public SkillGapResponse seekerSkillGap(UserAccount seeker, UUID jobId) {
        ResumeMetadata resume = resumeMetadataRepository()
                    .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                            seeker.getId(), ResumeProcessingStatus.PARSED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        Job job = jobRepository().findByIdAndStatus(jobId, JobStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        ScoredMatch breakdown = scoreResumeAgainstJob(matchScorer.prepareResume(resume), job, seeker);
        return new SkillGapResponse(jobId, breakdown.missingSkills());
    }

    public ScoredMatch scoreResumeAgainstJob(ResumeMetadata resume, Job job, UserAccount seeker) {
        return matchScorer.score(resume, job, seeker);
    }

    private ScoredMatch scoreResumeAgainstJob(
            MatchScorer.PreparedResume preparedResume, Job job, UserAccount seeker) {
        return matchScorer.score(preparedResume, job, seeker);
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
        if (!isEligible(seeker, job)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate is not eligible for this job");
        }
        return scoreResumeAgainstJob(matchScorer.prepareResume(resume), job, seeker);
    }

    private EmployerCandidateMatchResponse.EmployerCandidateMatchItem toEmployerItem(
            ResumeMetadata resume, ScoredMatch scored, CandidateMatchStatus shortlistStatus) {
        UserAccount seeker = resume.getSeeker();
        UUID seekerId = seeker == null ? null : seeker.getId();
        String displayName = seeker == null || seeker.getDisplayName() == null || seeker.getDisplayName().isBlank()
                ? null
                : seeker.getDisplayName();
        return new EmployerCandidateMatchResponse.EmployerCandidateMatchItem(
                resume.getId(), seekerId, displayName, scored.overallScore(), scored.factors(), scored.missingSkills(),
                shortlistStatus);
    }

    private Map<UUID, CandidateMatchStatus> shortlistStatuses(UUID jobId, List<ScoredResume> scored) {
        CandidateMatchNotificationRepository repository = shortlistRepositoryProvider.getIfAvailable();
        if (repository == null || jobId == null || scored.isEmpty()) {
            return Map.of();
        }
        Set<UUID> seekerIds = scored.stream()
                .map(item -> seekerId(item.resume()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (seekerIds.isEmpty()) {
            return Map.of();
        }
        List<CandidateMatchNotification> notifications = repository.findAllByJobIdAndSeekerIdIn(jobId, seekerIds);
        if (notifications == null || notifications.isEmpty()) {
            return Map.of();
        }
        Map<UUID, CandidateMatchStatus> statuses = new HashMap<>();
        for (CandidateMatchNotification notification : notifications) {
            if (notification == null || notification.getSeeker() == null
                    || notification.getSeeker().getId() == null) {
                continue;
            }
            statuses.put(notification.getSeeker().getId(), notification.getStatus());
        }
        return statuses;
    }

    private UUID seekerId(ResumeMetadata resume) {
        UserAccount seeker = resume == null ? null : resume.getSeeker();
        return seeker == null ? null : seeker.getId();
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
                job.getTitle(), job.getCompanyName(), MatchingPreferences.parseSectors(job.getSectorTags()),
                job.getLocation(), job.getWorkMode(), job.getSalaryMin(), job.getSalaryMax(),
                job.getEducationRequirement(), requiredSkills);
    }

    private boolean isEligible(UserAccount seeker, Job job) {
        return MatchingPreferences.sectorMatches(seeker.getPreferredSectors(), job.getSectorTags())
                && MatchingPreferences.workModeMatches(seeker.getWorkMode(), job.getWorkMode());
    }

    private <T> List<T> pageSlice(List<T> values, int offset, int limit) {
        if (offset >= values.size()) {
            return List.of();
        }
        return values.subList(offset, Math.min(values.size(), offset + limit));
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

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
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

    private record ScoredJob(Job job, ScoredMatch breakdown) {
    }

    private record ScoredResume(ResumeMetadata resume, ScoredMatch breakdown) {
    }
}
