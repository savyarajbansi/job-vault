package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
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
    private static final int SCAN_BATCH_SIZE = 500;

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider;
    private final MatchScorer matchScorer;

    public MatchingFacade(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            ObjectProvider<ResumeMetadataRepository> resumeMetadataRepositoryProvider,
            MatchScorer matchScorer) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.resumeMetadataRepositoryProvider = resumeMetadataRepositoryProvider;
        this.matchScorer = matchScorer;
    }

    @Transactional(readOnly = true)
    public SeekerJobMatchResponse seekerMatches(UserAccount seeker, int limit, int offset) {
        MatchPagination.validate(limit, offset);
        ResumeMetadata resume = resumeMetadataRepository()
                .findFirstBySeekerIdAndProcessingStatusOrderByParsedAtDescCreatedAtDesc(
                        seeker.getId(), ResumeProcessingStatus.PARSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        MatchScorer.PreparedResume preparedResume = matchScorer.prepareResume(resume, matchScorer.newContext());

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
                        item.breakdown().strongMatch(),
                        item.breakdown().factors(),
                        toJobInfo(item.job()),
                        item.breakdown().missingSkills()))
                .toList();
        return new SeekerJobMatchResponse(items, new MatchPage(limit, offset, toInt(eligibleTotal)));
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
}
