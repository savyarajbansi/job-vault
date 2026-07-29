// package com.project8.jobvault.matching;

// import com.project8.jobvault.jobs.Job;
// import com.project8.jobvault.resumes.ResumeMetadata;
// import com.project8.jobvault.users.UserAccount;
// import java.util.Set;
// import org.junit.jupiter.api.Test;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertFalse;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.when;

// class MatchScorerFactorTest {
//     private static final double EPSILON = 1e-6;

//     @Test
//     void sectorIsAWeightedAvailableFactorAndSupportsFuzzyTokenOverlap() {
//         MatchScorer scorer = scorer();
//         UserAccount seeker = user(5, "Information Technology");
//         Job job = job(5, "Information Tech");

//         MatchFactorBreakdown factors = scorer.score(resume(), job, seeker).factors();

//         assertEquals(1.0, factors.sector(), EPSILON);
//         assertTrue(factors.sectorAvailable());
//     }

//     @Test
//     void experienceUsesAOneYearPartialBandThenZero() {
//         MatchScorer scorer = scorer();
//         Job job = job(5, null);

//         assertEquals(1.0, scorer.score(resume(), job, user(5, null)).factors().experience(), EPSILON);
//         assertEquals(0.5, scorer.score(resume(), job, user(4, null)).factors().experience(), EPSILON);
//         assertEquals(0.0, scorer.score(resume(), job, user(3, null)).factors().experience(), EPSILON);
//     }

//     @Test
//     void locationUsesCityAndRegionNormalization() {
//         MatchScorer scorer = scorer();
//         Job job = job(0, null);
//         when(job.getLocation()).thenReturn("Austin, Texas");

//         MatchFactorBreakdown factors = scorer.score(resume(), job, user(0, null, "Austin, TX")).factors();

//         assertEquals(1.0, factors.location(), EPSILON);
//         assertTrue(factors.locationAvailable());
//     }

//     private MatchScorer scorer() {
//         CorpusIdfService corpus = mock(CorpusIdfService.class);
//         when(corpus.getSnapshot()).thenReturn(new CorpusIdfService.CorpusSnapshot(java.util.Map.of(), "test"));
//         return new MatchScorer(corpus, mock(com.project8.jobvault.parsing.SkillCatalog.class));
//     }

//     private ResumeMetadata resume() {
//         ResumeMetadata resume = mock(ResumeMetadata.class);
//         when(resume.getParsedText()).thenReturn("java spring");
//         when(resume.getInferredSkills()).thenReturn("");
//         return resume;
//     }

//     private Job job(int minExperience, String sector) {
//         Job job = mock(Job.class);
//         when(job.getTitle()).thenReturn("Engineer");
//         when(job.getDescription()).thenReturn("java");
//         when(job.getRequiredSkills()).thenReturn(Set.of());
//         when(job.getMinExperienceYears()).thenReturn(minExperience);
//         when(job.getSector()).thenReturn(sector);
//         return job;
//     }

//     private UserAccount user(int years, String sector) {
//         return user(years, sector, null);
//     }

//     private UserAccount user(int years, String sector, String location) {
//         UserAccount user = mock(UserAccount.class);
//         when(user.getYearsExperience()).thenReturn(years);
//         when(user.getPreferredSector()).thenReturn(sector);
//         when(user.getPreferredLocation()).thenReturn(location);
//         when(user.getRemoteOk()).thenReturn(false);
//         return user;
//     }
// }
