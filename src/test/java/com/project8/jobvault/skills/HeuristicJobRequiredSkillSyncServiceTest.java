package com.project8.jobvault.skills;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.parsing.SkillCatalog;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeuristicJobRequiredSkillSyncServiceTest {

    @Test
    void syncAddsDetectedSkillsWithoutRemovingExistingOnes() {
        JobRepository jobRepository = mock(JobRepository.class);
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        SkillRepository skillRepository = mock(SkillRepository.class);

        UUID jobId = UUID.randomUUID();
        Job job = new TestJob();
        job.setId(jobId);
        job.setDescription("We use Java and Spring.");

        Skill manuallyAdded = skillWithName("kubernetes");
        job.setRequiredSkills(new LinkedHashSet<>(List.of(manuallyAdded)));

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(skillCatalog.extractSkills(job.getDescription())).thenReturn(List.of("java", "spring"));
        when(skillRepository.findByNameIgnoreCase("java")).thenReturn(Optional.empty());
        when(skillRepository.findByNameIgnoreCase("spring")).thenReturn(Optional.empty());
        when(skillRepository.save(argThat(skill -> skill != null && "java".equals(skill.getName()))))
                .thenAnswer(invocation -> {
                    Skill created = invocation.getArgument(0);
                    created.setId(UUID.randomUUID());
                    return created;
                });
        when(skillRepository.save(argThat(skill -> skill != null && "spring".equals(skill.getName()))))
                .thenAnswer(invocation -> {
                    Skill created = invocation.getArgument(0);
                    created.setId(UUID.randomUUID());
                    return created;
                });

        HeuristicJobRequiredSkillSyncService service =
                new HeuristicJobRequiredSkillSyncService(jobRepository, skillCatalog, skillRepository);

        service.syncRequiredSkills(job);

        Set<String> names = job.getRequiredSkills().stream()
                .map(Skill::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("kubernetes", "java", "spring"), names);
        verify(jobRepository).save(job);
    }

    @Test
    void syncIsNoOpForBlankDescription() {
        JobRepository jobRepository = mock(JobRepository.class);
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        SkillRepository skillRepository = mock(SkillRepository.class);

        UUID jobId = UUID.randomUUID();
        Job job = new TestJob();
        job.setId(jobId);
        job.setDescription("   ");

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        HeuristicJobRequiredSkillSyncService service =
                new HeuristicJobRequiredSkillSyncService(jobRepository, skillCatalog, skillRepository);

        service.syncRequiredSkills(job);

        verify(jobRepository, never()).save(any());
        verify(skillCatalog, never()).extractSkills(anyString());
    }

    @Test
    void syncIsNoOpWhenJobHasNoId() {
        JobRepository jobRepository = mock(JobRepository.class);
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        SkillRepository skillRepository = mock(SkillRepository.class);

        Job job = new TestJob();

        HeuristicJobRequiredSkillSyncService service =
                new HeuristicJobRequiredSkillSyncService(jobRepository, skillCatalog, skillRepository);

        service.syncRequiredSkills(job);

        verify(jobRepository, never()).findById(any());
    }

    private Skill skillWithName(String name) {
        Skill skill = new TestSkill();
        skill.setId(UUID.randomUUID());
        skill.setName(name);
        return skill;
    }

    static final class TestJob extends Job {
    }

    static final class TestSkill extends Skill {
    }
}
