package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.parsing.SkillCatalog;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.users.UserAccount;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MatchScorer {
    static final double COSINE_WEIGHT = 0.4;
    static final double SKILL_WEIGHT = 0.35;
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
        List<String> resumeTokens = textTokenizer.tokenize(orEmpty(resume == null ? null : resume.getParsedText()));
        String jobText = ((job == null ? null : job.getTitle()) == null ? "" : job.getTitle())
                + " " + orEmpty(job == null ? null : job.getDescription());
        List<String> jobTokens = textTokenizer.tokenize(jobText);

        CorpusIdfService.CorpusSnapshot snapshot = corpusIdfService.getSnapshot();
        var idf = snapshot.idfByTerm();
        if (idf.isEmpty()) {
            idf = InverseDocumentFrequency.compute(List.of(resumeTokens, jobTokens));
        }
        TfIdfVectorizer vectorizer = new TfIdfVectorizer(idf);
        double cosine = CosineSimilarity.compute(
                vectorizer.vectorize(resumeTokens), vectorizer.vectorize(jobTokens));
        boolean cosineAvailable = !resumeTokens.isEmpty() && !jobTokens.isEmpty();

        Set<String> requiredSkills = requiredSkills(job);
        Set<String> resumeSkills = splitSkills(resume == null ? null : resume.getInferredSkills());
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
        return new ExperienceResult(clamp01((double) years / required), true);
    }

    private LocationResult locationScore(UserAccount seeker, Job job) {
        String seekerLocation = normalizeLocation(seeker == null ? null : seeker.getPreferredLocation());
        String jobLocation = normalizeLocation(job == null ? null : job.getLocation());
        boolean remoteEligible = job != null && Boolean.TRUE.equals(job.getRemoteEligible());
        Boolean remoteOk = seeker == null ? null : seeker.getRemoteOk();
        boolean hasLocationSignal = seekerLocation != null && jobLocation != null;
        boolean hasRemoteSignal = remoteEligible && remoteOk != null;
        if (!hasLocationSignal && !hasRemoteSignal) {
            return new LocationResult(0.0, false);
        }
        boolean matches = hasLocationSignal && locationMatches(seekerLocation, jobLocation);
        if (remoteEligible && Boolean.TRUE.equals(remoteOk)) {
            matches = true;
        }
        return new LocationResult(matches ? 1.0 : 0.0, true);
    }

    private boolean locationMatches(String seekerLocation, String jobLocation) {
        Set<String> seekerTokens = tokenizeLocation(seekerLocation);
        Set<String> jobTokens = tokenizeLocation(jobLocation);
        for (String token : seekerTokens) {
            if (token.length() > 2 && jobTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokenizeLocation(String location) {
        return Arrays.stream(location.toLowerCase(Locale.ROOT).split("[,\\s]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toSet());
    }

    private String normalizeLocation(String location) {
        if (location == null) {
            return null;
        }
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
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
}
