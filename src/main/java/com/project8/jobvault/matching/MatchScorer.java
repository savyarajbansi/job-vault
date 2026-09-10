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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MatchScorer {
    static final double COSINE_WEIGHT = 0.35;
    static final double SKILL_WEIGHT = 0.3;
    static final double EXPERIENCE_WEIGHT = 0.15;
    static final double LOCATION_WEIGHT = 0.1;

    private final CorpusIdfService corpusIdfService;
    private final SkillCatalog skillCatalog;
    private final TextTokenizer textTokenizer = new TextTokenizer(MatchingStopwords.DEFAULT);

    public MatchScorer(CorpusIdfService corpusIdfService, SkillCatalog skillCatalog) {
        this.corpusIdfService = corpusIdfService;
        this.skillCatalog = skillCatalog;
    }

    public ScoredMatch score(ResumeMetadata resume, Job job, UserAccount seeker) {
        return score(prepareResume(resume), job, seeker);
    }

    /**
     * Prepares the resume-only portions of a score once. Ranking a seeker
     * against many jobs used to tokenize and vectorize the same resume for
     * every job, and could even observe a different IDF snapshot mid-request.
     */
    public PreparedResume prepareResume(ResumeMetadata resume) {
        return prepareResume(resume, newContext());
    }

    /** Captures the immutable corpus data used by all scores in one request. */
    public ScoringContext newContext() {
        return new ScoringContext(corpusIdfService.getSnapshot().idfByTerm());
    }

    public PreparedResume prepareResume(ResumeMetadata resume, ScoringContext context) {
        Objects.requireNonNull(context, "context");
        List<String> resumeTokens = textTokenizer.tokenize(orEmpty(resume == null ? null : resume.getParsedText()));
        Set<String> resumeSkills = splitSkills(resume == null ? null : resume.getInferredSkills());
        Map<String, Double> idf = context.idfByTerm();
        Map<String, Double> resumeVector = idf.isEmpty()
                ? Map.of()
                : new TfIdfVectorizer(idf).vectorize(resumeTokens);
        return new PreparedResume(resumeTokens, resumeSkills, idf, resumeVector);
    }

    public ScoredMatch score(PreparedResume prepared, Job job, UserAccount seeker) {
        Objects.requireNonNull(prepared, "prepared");
        List<String> resumeTokens = prepared.tokens();
        String jobText = ((job == null ? null : job.getTitle()) == null ? "" : job.getTitle())
                + " " + orEmpty(job == null ? null : job.getDescription());
        List<String> jobTokens = textTokenizer.tokenize(jobText);

        Map<String, Double> idf = prepared.idfByTerm();
        if (idf.isEmpty()) {
            idf = InverseDocumentFrequency.compute(List.of(resumeTokens, jobTokens));
        }
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(idf);
        Map<String, Double> resumeVector = prepared.resumeVector().isEmpty()
                ? vectorizer.vectorize(resumeTokens)
                : prepared.resumeVector();
        Map<String, Double> jobVector = vectorizer.vectorize(jobTokens);
        double cosine = CosineSimilarity.compute(
                resumeVector, jobVector);
        // A token list alone is not enough: when the corpus does not contain
        // a resume term, vectorization can legitimately produce an empty
        // vector. In that case cosine similarity should not dilute the other
        // available factors.
        boolean cosineAvailable = !resumeVector.isEmpty() && !jobVector.isEmpty();

        Set<String> requiredSkills = requiredSkills(job);
        Set<String> resumeSkills = prepared.skills();
        int overlapCount = (int) requiredSkills.stream().filter(resumeSkills::contains).count();
        boolean skillsAvailable = !requiredSkills.isEmpty();
        double skillsOverlap = skillsAvailable ? (double) overlapCount / requiredSkills.size() : 0.0;
        List<String> missingSkills = requiredSkills.stream()
                .filter(skill -> !resumeSkills.contains(skill))
                .sorted()
                .toList();

        ExperienceResult experience = experienceScore(seeker, job);
        LocationResult location = locationScore(seeker, job);
        double weightedTotal = 0.0;
        double activeWeight = 0.0;
        if (cosineAvailable) {
            weightedTotal += cosine * COSINE_WEIGHT;
            activeWeight += COSINE_WEIGHT;
        }
        if (skillsAvailable) {
            weightedTotal += skillsOverlap * SKILL_WEIGHT;
            activeWeight += SKILL_WEIGHT;
        }
        if (experience.available()) {
            weightedTotal += experience.value() * EXPERIENCE_WEIGHT;
            activeWeight += EXPERIENCE_WEIGHT;
        }
        if (location.available()) {
            weightedTotal += location.value() * LOCATION_WEIGHT;
            activeWeight += LOCATION_WEIGHT;
        }
        double overall = activeWeight == 0.0 ? 0.0 : clamp01(weightedTotal / activeWeight);
        return new ScoredMatch(
                overall,
                new MatchFactorBreakdown(
                        cosine,
                        skillsOverlap,
                        experience.value(),
                        location.value(),
                        cosineAvailable,
                        skillsAvailable,
                        experience.available(),
                        location.available()),
                missingSkills);
    }

    public String canonicalizeSkill(String value) {
        return skillCatalog.canonicalize(value);
    }

    private Set<String> requiredSkills(Job job) {
        if (job == null || job.getRequiredSkills() == null) {
            return Set.of();
        }
        return job.getRequiredSkills().stream()
                .map(skill -> skill == null ? null : skill.getName())
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

    private ExperienceResult experienceScore(UserAccount seeker, Job job) {
        Integer required = job == null ? null : job.getMinExperienceYears();
        Integer years = seeker == null ? null : seeker.getYearsExperience();
        if (required == null || required <= 0 || years == null) {
            return new ExperienceResult(0.0, false);
        }
        if (years <= 0) {
            return new ExperienceResult(0.0, true);
        }
        if (years >= required) {
            return new ExperienceResult(1.0, true);
        }
        int partialThreshold = Math.max(0, required - 1);
        if (years < partialThreshold) {
            return new ExperienceResult(0.0, true);
        }
        return new ExperienceResult(0.5, true);
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ExperienceResult(double value, boolean available) {
    }

    private record LocationResult(double value, boolean available) {
    }

    public record PreparedResume(
            List<String> tokens,
            Set<String> skills,
            Map<String, Double> idfByTerm,
            Map<String, Double> resumeVector) {
        public PreparedResume {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
            skills = skills == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(skills));
            idfByTerm = idfByTerm == null ? Map.of() : Map.copyOf(idfByTerm);
            resumeVector = resumeVector == null ? Map.of() : Map.copyOf(resumeVector);
        }
    }

    public record ScoringContext(Map<String, Double> idfByTerm) {
        public ScoringContext {
            idfByTerm = idfByTerm == null ? Map.of() : Map.copyOf(idfByTerm);
        }
    }

}
