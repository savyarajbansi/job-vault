package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.matching.WorkMode;
import com.project8.jobvault.parsing.SkillCatalog;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.users.UserAccount;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MatchScorer {
    public static final double STRONG_MATCH_THRESHOLD = 0.70;
    public static final double BM25_WEIGHT = 0.45;
    public static final double EMBEDDING_WEIGHT = 0.20;
    public static final double EXPERIENCE_WEIGHT = 0.15;
    public static final double LOCATION_WEIGHT = 0.10;
    public static final double SALARY_WEIGHT = 0.10;
    private static final double REQUIRED_SKILLS_LEXICAL_WEIGHT = 0.60;
    private static final double TITLE_LEXICAL_WEIGHT = 0.25;
    private static final double DESCRIPTION_LEXICAL_WEIGHT = 0.15;

    private final MatchingCorpusService matchingCorpusService;
    private final MatchingTextPreprocessor textPreprocessor;
    private final EmbeddingService embeddingService;
    private final SkillCatalog skillCatalog;

    public MatchScorer(
            MatchingCorpusService matchingCorpusService,
            MatchingTextPreprocessor textPreprocessor,
            EmbeddingService embeddingService,
            SkillCatalog skillCatalog) {
        this.matchingCorpusService = matchingCorpusService;
        this.textPreprocessor = textPreprocessor;
        this.embeddingService = embeddingService;
        this.skillCatalog = skillCatalog;
    }

    public ScoredMatch score(ResumeMetadata resume, Job job, UserAccount seeker) {
        return score(prepareResume(resume), job, seeker);
    }

    public PreparedResume prepareResume(ResumeMetadata resume) {
        return prepareResume(resume, newContext());
    }

    public ScoringContext newContext() {
        return new ScoringContext(matchingCorpusService.getSnapshot());
    }

    public PreparedResume prepareResume(ResumeMetadata resume, ScoringContext context) {
        Objects.requireNonNull(context, "context");
        String resumeText = resume == null || resume.getParsedText() == null ? "" : resume.getParsedText();
        List<String> resumeTokens = textPreprocessor.tokenize(resumeText);
        Set<String> resumeSkills = splitSkills(resume == null ? null : resume.getInferredSkills());
        double[] embedding = embeddingService.embed(resumeText).orElse(null);
        return new PreparedResume(resumeTokens, resumeSkills, embedding, context.snapshot());
    }

    public ScoredMatch score(PreparedResume prepared, Job job, UserAccount seeker) {
        Objects.requireNonNull(prepared, "prepared");
        MatchingCorpusService.CorpusSnapshot snapshot = prepared.snapshot();
        MatchingCorpusService.JobDocument corpusDocument = job == null || job.getId() == null
                ? null
                : snapshot.jobs().get(job.getId());
        List<String> titleTokens = corpusDocument == null
                ? textPreprocessor.tokenize(job == null ? "" : job.getTitle())
                : corpusDocument.titleTokens();
        List<String> descriptionTokens = corpusDocument == null
                ? textPreprocessor.tokenize(job == null ? "" : job.getDescription())
                : corpusDocument.descriptionTokens();

        Set<String> requiredSkills = requiredSkills(job);
        LexicalResult lexical = lexicalScore(
                prepared.tokens(),
                prepared.skills(),
                requiredSkills,
                titleTokens,
                descriptionTokens,
                snapshot);
        Bm25Scorer.Result bm25 = lexical.result();
        double embedding = 0.0;
        boolean embeddingAvailable = false;
        double[] jobEmbedding = corpusDocument == null ? null : corpusDocument.embedding();
        if (prepared.embedding() != null && jobEmbedding != null) {
            Double cosine = cosine(prepared.embedding(), jobEmbedding);
            if (cosine != null) {
                embedding = clamp01((cosine + 1.0) / 2.0);
                embeddingAvailable = true;
            }
        }

        Set<String> resumeSkills = prepared.skills();
        List<String> missingSkills = requiredSkills.stream()
                .filter(skill -> !resumeSkills.contains(skill))
                .sorted()
                .toList();

        ExperienceResult experience = experienceScore(seeker, job);
        LocationResult location = locationScore(seeker, job);
        SalaryResult salary = salaryScore(seeker, job);
        double weightedTotal = 0.0;
        double activeWeight = 0.0;
        if (bm25.available()) {
            weightedTotal += bm25.value() * BM25_WEIGHT;
            activeWeight += BM25_WEIGHT;
        }
        if (embeddingAvailable) {
            weightedTotal += embedding * EMBEDDING_WEIGHT;
            activeWeight += EMBEDDING_WEIGHT;
        }
        if (experience.available()) {
            weightedTotal += experience.value() * EXPERIENCE_WEIGHT;
            activeWeight += EXPERIENCE_WEIGHT;
        }
        if (location.available()) {
            weightedTotal += location.value() * LOCATION_WEIGHT;
            activeWeight += LOCATION_WEIGHT;
        }
        if (salary.available()) {
            weightedTotal += salary.value() * SALARY_WEIGHT;
            activeWeight += SALARY_WEIGHT;
        }
        double overall = activeWeight == 0.0 ? 0.0 : clamp01(weightedTotal / activeWeight);
        boolean strongMatch = overall >= STRONG_MATCH_THRESHOLD && activeWeight > 0.0;
        return new ScoredMatch(
                overall,
                new MatchFactorBreakdown(
                        bm25.value(),
                        embedding,
                        experience.value(),
                        location.value(),
                        salary.value(),
                        bm25.available(),
                        embeddingAvailable,
                        experience.available(),
                        location.available(),
                        salary.available(),
                        lexical.breakdown()),
                missingSkills,
                strongMatch);
    }

    public String canonicalizeSkill(String value) {
        return skillCatalog.canonicalize(value);
    }

    private Set<String> requiredSkills(Job job) {
        if (job == null || job.getRequiredSkills() == null) {
            return Set.of();
        }
        return job.getRequiredSkills().stream()
                .map(skill -> skill == null ? "" : skill.getName())
                .map(skillCatalog::canonicalize)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> splitSkills(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(skillCatalog::canonicalize)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private LexicalResult lexicalScore(
            List<String> resumeTokens,
            Set<String> resumeSkills,
            Set<String> requiredSkills,
            List<String> titleTokens,
            List<String> descriptionTokens,
            MatchingCorpusService.CorpusSnapshot snapshot) {
        boolean requiredSkillsAvailable = !requiredSkills.isEmpty();
        double requiredSkillCoverage = requiredSkillsAvailable
                ? requiredSkills.stream().filter(resumeSkills::contains).count() / (double) requiredSkills.size()
                : 0.0;

        Bm25Scorer.Result title = Bm25Scorer.score(
                resumeTokens, titleTokens, snapshot.idfByTerm(), snapshot.averageTitleLength());
        Bm25Scorer.Result description = Bm25Scorer.score(
                resumeTokens, descriptionTokens, snapshot.idfByTerm(), snapshot.averageDescriptionLength());

        double weightedTotal = 0.0;
        double activeWeight = 0.0;
        if (requiredSkillsAvailable) {
            weightedTotal += requiredSkillCoverage * REQUIRED_SKILLS_LEXICAL_WEIGHT;
            activeWeight += REQUIRED_SKILLS_LEXICAL_WEIGHT;
        }
        if (title.available()) {
            weightedTotal += title.value() * TITLE_LEXICAL_WEIGHT;
            activeWeight += TITLE_LEXICAL_WEIGHT;
        }
        if (description.available()) {
            weightedTotal += description.value() * DESCRIPTION_LEXICAL_WEIGHT;
            activeWeight += DESCRIPTION_LEXICAL_WEIGHT;
        }

        Bm25Scorer.Result result = activeWeight == 0.0
                ? new Bm25Scorer.Result(0.0, false)
                : new Bm25Scorer.Result(clamp01(weightedTotal / activeWeight), true);
        return new LexicalResult(
                result,
                new MatchFactorBreakdown.LexicalBreakdown(
                        requiredSkillCoverage,
                        title.value(),
                        description.value(),
                        requiredSkillsAvailable,
                        title.available(),
                        description.available()));
    }

    private Double cosine(double[] left, double[] right) {
        if (left.length == 0 || right.length == 0 || left.length != right.length) {
            return null;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return null;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private ExperienceResult experienceScore(UserAccount seeker, Job job) {
        Integer required = job == null ? null : job.getMinExperienceYears();
        Integer years = seeker == null ? null : seeker.getYearsExperience();
        if (required == null || required <= 0 || years == null) {
            return new ExperienceResult(0.0, false);
        }
        double ratio = (double) Math.max(0, years) / required;
        return new ExperienceResult(Math.min(1.0, ratio), true);
    }

    private LocationResult locationScore(UserAccount seeker, Job job) {
        String seekerLocation = seeker == null ? null : seeker.getPreferredLocation();
        String jobLocation = job == null ? null : job.getLocation();
        boolean remoteEligible = job != null && job.getWorkMode() != null && job.getWorkMode() != WorkMode.ON_SITE;
        WorkMode preference = seeker == null ? null : seeker.getWorkMode();
        boolean hasLocationSignal = LocationNormalizer.normalize(seekerLocation) != null
                && LocationNormalizer.normalize(jobLocation) != null;
        boolean hasRemoteSignal = remoteEligible && preference != null;
        if (!hasLocationSignal && !hasRemoteSignal) {
            return new LocationResult(0.0, false);
        }
        boolean matches = hasLocationSignal && LocationNormalizer.matches(seekerLocation, jobLocation);
        if (remoteEligible && preference != WorkMode.ON_SITE) {
            matches = true;
        }
        return new LocationResult(matches ? 1.0 : 0.0, true);
    }

    private SalaryResult salaryScore(UserAccount seeker, Job job) {
        Integer preferredMin = seeker == null ? null : seeker.getPreferredSalaryMin();
        Integer preferredMax = seeker == null ? null : seeker.getPreferredSalaryMax();
        Integer jobMin = job == null ? null : job.getSalaryMin();
        Integer jobMax = job == null ? null : job.getSalaryMax();
        if (preferredMin == null && preferredMax == null || jobMin == null && jobMax == null) {
            return new SalaryResult(0.0, false);
        }

        boolean seekerMinimumExceedsJobMaximum = preferredMin != null
                && jobMax != null
                && preferredMin > jobMax;
        return new SalaryResult(seekerMinimumExceedsJobMaximum ? 0.0 : 1.0, true);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ExperienceResult(double value, boolean available) {
    }

    private record LocationResult(double value, boolean available) {
    }

    private record SalaryResult(double value, boolean available) {
    }

    private record LexicalResult(
            Bm25Scorer.Result result,
            MatchFactorBreakdown.LexicalBreakdown breakdown) {
    }

    public record PreparedResume(
            List<String> tokens,
            Set<String> skills,
            double[] embedding,
            MatchingCorpusService.CorpusSnapshot snapshot) {
        public PreparedResume {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
            skills = skills == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(skills));
            embedding = embedding == null ? null : embedding.clone();
            snapshot = snapshot == null ? MatchingCorpusService.CorpusSnapshot.empty() : snapshot;
        }

        @Override
        public double[] embedding() {
            return embedding == null ? null : embedding.clone();
        }
    }

    public record ScoringContext(MatchingCorpusService.CorpusSnapshot snapshot) {
        public ScoringContext {
            snapshot = snapshot == null ? MatchingCorpusService.CorpusSnapshot.empty() : snapshot;
        }
    }
}
