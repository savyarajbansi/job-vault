package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.parsing.SkillCatalog;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.users.UserAccount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchScorerTest {
    @Test
    void unavailableLexicalAndEmbeddingFactorsDoNotDiluteStructuredSignals() {
        MatchScorer scorer = scorer(MatchingCorpusService.CorpusSnapshot.empty(), Optional.empty());
        ScoredMatch scored = scorer.score(resume("resume-only-term", ""), job(Set.of(), 5, ""), user(5));

        assertFalse(scored.factors().bm25Available());
        assertFalse(scored.factors().embeddingAvailable());
        assertEquals(1.0, scored.overallScore(), 1e-6);
    }

    @Test
    void preparedResumeReusesOneCorpusSnapshotAndOneEmbeddingAcrossManyJobs() {
        UUID jobId = UUID.randomUUID();
        MatchingCorpusService.CorpusSnapshot snapshot = snapshot(jobId, List.of("java"), new double[] { 1, 0 });
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(anyString())).thenReturn(Optional.of(new double[] { 1, 0 }));
        MatchingCorpusService corpus = mock(MatchingCorpusService.class);
        when(corpus.getSnapshot()).thenReturn(snapshot);
        MatchScorer scorer = scorer(corpus, embeddings);
        ResumeMetadata resume = resume("java", "java");
        Job job = jobWithId(jobId, Set.of(), null, "java");

        MatchScorer.PreparedResume prepared = scorer.prepareResume(resume);
        scorer.score(prepared, job, null);
        scorer.score(prepared, job, null);

        verify(corpus, times(1)).getSnapshot();
        verify(embeddings, times(1)).embed("java");
    }

    @Test
    void embeddingCosineIsMappedToTheBoundedApiScore() {
        UUID jobId = UUID.randomUUID();
        MatchScorer scorer = scorer(
                snapshot(jobId, List.of("java"), new double[] { 1, 0 }),
                Optional.of(new double[] { 0, 1 }));
        ScoredMatch scored = scorer.score(resume("java", ""), jobWithId(jobId, Set.of(), null, "java"), null);

        assertTrue(scored.factors().embeddingAvailable());
        assertEquals(0.5, scored.factors().embedding(), 1e-6);
        assertTrue(scored.factors().embedding() >= 0.0 && scored.factors().embedding() <= 1.0);
    }

    @Test
    void experienceUsesAProportionalRatioAndCapsAtOne() {
        MatchScorer scorer = scorer(MatchingCorpusService.CorpusSnapshot.empty(), Optional.empty());
        ResumeMetadata resume = resume("", "");
        Job job = job(Set.of(), 10, "");

        assertEquals(0.20, scorer.score(resume, job, user(2)).factors().experience(), 1e-6);
        assertEquals(1.0, scorer.score(resume, job, user(10)).factors().experience(), 1e-6);
        assertEquals(1.0, scorer.score(resume, job, user(15)).factors().experience(), 1e-6);
        assertEquals(0.0, scorer.score(resume, job, user(0)).factors().experience(), 1e-6);
    }

    @Test
    void salaryOverlapIsAnAvailableMatchingFactor() {
        MatchScorer scorer = scorer(MatchingCorpusService.CorpusSnapshot.empty(), Optional.empty());
        Job job = job(Set.of(), null, "");
        when(job.getSalaryMin()).thenReturn(80000);
        when(job.getSalaryMax()).thenReturn(120000);
        UserAccount seeker = user(5);
        when(seeker.getPreferredSalaryMin()).thenReturn(90000);
        when(seeker.getPreferredSalaryMax()).thenReturn(110000);

        ScoredMatch scored = scorer.score(resume("", ""), job, seeker);

        assertTrue(scored.factors().salaryAvailable());
        assertEquals(1.0, scored.factors().salary(), 1e-6);
    }

    @Test
    void seekerMinimumAboveJobMaximumReceivesNoSalaryFit() {
        MatchScorer scorer = scorer(MatchingCorpusService.CorpusSnapshot.empty(), Optional.empty());
        Job job = job(Set.of(), null, "");
        when(job.getSalaryMin()).thenReturn(80000);
        when(job.getSalaryMax()).thenReturn(90000);
        UserAccount seeker = user(5);
        when(seeker.getPreferredSalaryMin()).thenReturn(100000);
        when(seeker.getPreferredSalaryMax()).thenReturn(120000);

        ScoredMatch scored = scorer.score(resume("", ""), job, seeker);

        assertTrue(scored.factors().salaryAvailable());
        assertEquals(0.0, scored.factors().salary(), 1e-6);
    }

    @Test
    void lowerSeekerExpectationsDoNotReduceSalaryFit() {
        MatchScorer scorer = scorer(MatchingCorpusService.CorpusSnapshot.empty(), Optional.empty());
        Job job = job(Set.of(), null, "");
        when(job.getSalaryMin()).thenReturn(120000);
        when(job.getSalaryMax()).thenReturn(150000);
        UserAccount seeker = user(5);
        when(seeker.getPreferredSalaryMin()).thenReturn(80000);
        when(seeker.getPreferredSalaryMax()).thenReturn(90000);

        ScoredMatch scored = scorer.score(resume("", ""), job, seeker);

        assertTrue(scored.factors().salaryAvailable());
        assertEquals(1.0, scored.factors().salary(), 1e-6);
    }

    @Test
    void missingRequiredSkillsLowerLexicalRelevance() {
        UUID jobId = UUID.randomUUID();
        MatchScorer scorer = scorer(snapshot(jobId, List.of("java"), new double[] { 1, 0 }),
                Optional.of(new double[] { 1, 0 }));
        ScoredMatch scored = scorer.score(
                resume("java", "java"),
                jobWithId(jobId, Set.of(skill("spring")), 10, "java"),
                user(10));

        assertEquals(List.of("spring"), scored.missingSkills());
        assertTrue(scored.factors().bm25() < 1.0);
        assertFalse(scored.strongMatch());
    }

    @Test
    void fieldAwareLexicalScoreUsesTheConfiguredSkillTitleAndDescriptionMix() {
        UUID jobId = UUID.randomUUID();
        MatchingCorpusService.CorpusSnapshot snapshot = new MatchingCorpusService.CorpusSnapshot(
                Map.of("java", 1.0, "spring", 1.0),
                1.0,
                1.0,
                Map.of(jobId, new MatchingCorpusService.JobDocument(
                        jobId, List.of("java"), List.of("spring"), null)),
                "test",
                "unavailable");
        MatchScorer scorer = scorer(snapshot, Optional.empty());
        Job job = jobWithId(jobId, Set.of(skill("java"), skill("spring")), null, "java");
        when(job.getDescription()).thenReturn("spring");

        ScoredMatch scored = scorer.score(resume("java", "java"), job, null);

        assertEquals(0.55, scored.factors().bm25(), 1e-6);
        assertEquals(0.50, scored.factors().lexical().requiredSkills(), 1e-6);
        assertEquals(1.0, scored.factors().lexical().title(), 1e-6);
        assertEquals(0.0, scored.factors().lexical().description(), 1e-6);
        assertTrue(scored.factors().lexical().requiredSkillsAvailable());
        assertTrue(scored.factors().lexical().titleAvailable());
        assertTrue(scored.factors().lexical().descriptionAvailable());
    }

    @Test
    void suppliedProfilesKeepExpectedRankingWithEmbeddingsUnavailable() {
        UUID jobId = UUID.randomUUID();
        List<String> requiredNames = List.of(
                "java", "spring boot", "rest", "microservices", "postgresql", "redis", "kafka",
                "docker", "aws", "ci/cd", "junit", "sql", "hibernate");
        Set<Skill> requiredSkills = requiredNames.stream().map(this::skill).collect(java.util.stream.Collectors.toSet());
        List<String> descriptionTokens = List.of(
                "java", "spring", "boot", "rest", "microservices", "postgresql", "redis", "kafka",
                "docker", "aws", "ci/cd", "junit", "sql", "hibernate");
        MatchingCorpusService.CorpusSnapshot snapshot = new MatchingCorpusService.CorpusSnapshot(
                Bm25Scorer.computeIdf(List.of(
                        List.of("senior", "backend", "engineer"),
                        descriptionTokens)),
                3.0,
                descriptionTokens.size(),
                Map.of(jobId, new MatchingCorpusService.JobDocument(
                        jobId, List.of("senior", "backend", "engineer"), descriptionTokens, null)),
                "test",
                "unavailable");
        MatchScorer scorer = scorer(snapshot, Optional.empty());
        Job job = jobWithId(jobId, requiredSkills, 5, "senior backend engineer");
        when(job.getDescription()).thenReturn(String.join(" ", descriptionTokens));

        List<ScoredMatch> scores = List.of(
                scorer.score(resume(
                        "senior backend engineer java spring boot rest microservices postgresql redis kafka docker aws ci/cd junit sql hibernate",
                        String.join(",", requiredNames)), job, user(5)),
                scorer.score(resume(
                        "backend engineer java spring boot rest microservices postgresql docker aws ci/cd junit sql hibernate",
                        "java,spring boot,rest,microservices,postgresql,docker,aws,ci/cd,junit,sql,hibernate"), job, user(5)),
                scorer.score(resume(
                        "backend developer rest postgresql redis docker aws ci/cd sql",
                        "rest,postgresql,redis,docker,aws,ci/cd,sql"), job, user(5)),
                scorer.score(resume(
                        "frontend engineer react typescript css graphql",
                        "react,typescript,css,graphql"), job, user(5)));

        assertTrue(scores.get(0).overallScore() > scores.get(1).overallScore());
        assertTrue(scores.get(1).overallScore() > scores.get(2).overallScore());
        assertTrue(scores.get(2).overallScore() > scores.get(3).overallScore());
        assertTrue(scores.stream().noneMatch(score -> score.factors().embeddingAvailable()));
    }

    @Test
    void lexicalPreprocessorUsesCanonicalUnigrams() {
        SkillCatalog catalog = mock(SkillCatalog.class);
        when(catalog.canonicalizeText("JS Springboot")).thenReturn("javascript spring boot");
        MatchingTextPreprocessor preprocessor = new MatchingTextPreprocessor(catalog);

        List<String> tokens = preprocessor.tokenize("JS Springboot");

        assertTrue(tokens.contains("javascript"));
        assertTrue(tokens.contains("spring"));
        assertTrue(tokens.contains("boot"));
        assertFalse(tokens.contains("spring_boot"));
    }

    private MatchScorer scorer(MatchingCorpusService.CorpusSnapshot snapshot, Optional<double[]> embedding) {
        MatchingCorpusService corpus = mock(MatchingCorpusService.class);
        when(corpus.getSnapshot()).thenReturn(snapshot);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(anyString())).thenReturn(embedding);
        return scorer(corpus, embeddings);
    }

    private MatchScorer scorer(MatchingCorpusService corpus, EmbeddingService embeddings) {
        MatchingTextPreprocessor preprocessor = mock(MatchingTextPreprocessor.class);
        when(preprocessor.tokenize(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0, String.class);
            return text == null || text.isBlank()
                    ? List.of()
                    : List.of(text.toLowerCase().trim().split("\\s+"));
        });
        SkillCatalog catalog = mock(SkillCatalog.class);
        when(catalog.canonicalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return new MatchScorer(corpus, preprocessor, embeddings, catalog);
    }

    private MatchingCorpusService.CorpusSnapshot snapshot(UUID jobId, List<String> tokens, double[] embedding) {
        return new MatchingCorpusService.CorpusSnapshot(
                Map.of("java", 1.0, "spring", 1.0),
                tokens.size(),
                Map.of(jobId, new MatchingCorpusService.JobDocument(jobId, tokens, embedding)),
                "test",
                "test-model");
    }

    private ResumeMetadata resume(String parsedText, String inferredSkills) {
        ResumeMetadata resume = mock(ResumeMetadata.class);
        when(resume.getParsedText()).thenReturn(parsedText);
        when(resume.getInferredSkills()).thenReturn(inferredSkills);
        return resume;
    }

    private Job job(Set<Skill> requiredSkills, Integer minExperienceYears, String text) {
        return jobWithId(UUID.randomUUID(), requiredSkills, minExperienceYears, text);
    }

    private Job jobWithId(UUID id, Set<Skill> requiredSkills, Integer minExperienceYears, String text) {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(id);
        when(job.getTitle()).thenReturn(text);
        when(job.getDescription()).thenReturn("");
        when(job.getRequiredSkills()).thenReturn(requiredSkills);
        when(job.getMinExperienceYears()).thenReturn(minExperienceYears);
        return job;
    }

    private Skill skill(String name) {
        Skill skill = mock(Skill.class);
        when(skill.getName()).thenReturn(name);
        return skill;
    }

    private UserAccount user(Integer years) {
        if (years == null) {
            return null;
        }
        UserAccount user = mock(UserAccount.class);
        when(user.getYearsExperience()).thenReturn(years);
        return user;
    }
}
