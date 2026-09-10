package com.project8.jobvault.matching;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.parsing.SkillCatalog;
import com.project8.jobvault.resumes.ResumeMetadata;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchScorerTest {

    @Test
    void doesNotTreatOutOfCorpusResumeTermsAsAnAvailableCosineSignal() {
        CorpusIdfService corpus = mock(CorpusIdfService.class);
        when(corpus.getSnapshot()).thenReturn(
                new CorpusIdfService.CorpusSnapshot(Map.of("job", 1.0), "snapshot"));
        SkillCatalog catalog = canonicalSkillCatalog();
        MatchScorer scorer = new MatchScorer(corpus, catalog);

        ResumeMetadata resume = mock(ResumeMetadata.class);
        when(resume.getParsedText()).thenReturn("resume-only-term");
        when(resume.getInferredSkills()).thenReturn("");
        Job job = mock(Job.class);
        when(job.getTitle()).thenReturn("job");
        when(job.getDescription()).thenReturn("");
        when(job.getRequiredSkills()).thenReturn(Set.of());

        ScoredMatch scored = scorer.score(resume, job, null);

        assertFalse(scored.factors().cosineAvailable());
        assertEquals(0.0, scored.overallScore());
    }

    @Test
    void preparedResumeReusesOneCorpusSnapshotAcrossManyJobs() {
        CorpusIdfService corpus = mock(CorpusIdfService.class);
        when(corpus.getSnapshot()).thenReturn(
                new CorpusIdfService.CorpusSnapshot(Map.of("java", 1.0), "snapshot"));
        MatchScorer scorer = new MatchScorer(corpus, canonicalSkillCatalog());
        ResumeMetadata resume = mock(ResumeMetadata.class);
        when(resume.getParsedText()).thenReturn("java");
        when(resume.getInferredSkills()).thenReturn("java");
        Job job = mock(Job.class);
        when(job.getTitle()).thenReturn("java");
        when(job.getDescription()).thenReturn("");
        when(job.getRequiredSkills()).thenReturn(Set.of());

        MatchScorer.PreparedResume prepared = scorer.prepareResume(resume);
        scorer.score(prepared, job, null);
        scorer.score(prepared, job, null);

        verify(corpus, times(1)).getSnapshot();
    }

    private SkillCatalog canonicalSkillCatalog() {
        SkillCatalog catalog = mock(SkillCatalog.class);
        when(catalog.canonicalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return catalog;
    }
}
