package com.project8.jobvault.skills;

import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobRequiredSkillSyncService;
import com.project8.jobvault.parsing.SkillCatalog;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(SkillRepository.class)
public class HeuristicJobRequiredSkillSyncService implements JobRequiredSkillSyncService {
    private final JobRepository jobRepository;
    private final SkillCatalog skillCatalog;
    private final SkillRepository skillRepository;

    public HeuristicJobRequiredSkillSyncService(
            JobRepository jobRepository,
            SkillCatalog skillCatalog,
            SkillRepository skillRepository) {
        this.jobRepository = jobRepository;
        this.skillCatalog = skillCatalog;
        this.skillRepository = skillRepository;
    }

    @Override
    @Transactional
    public void syncRequiredSkills(Job job) {
        if (job == null || job.getDescription() == null || job.getDescription().isBlank()) {
            return;
        }
        Set<Skill> required = new LinkedHashSet<>();
        for (String inferred : skillCatalog.extractSkills(job.getDescription())) {
            String normalized = inferred == null ? "" : inferred.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            Skill skill = skillRepository.findByNameIgnoreCase(normalized)
                    .orElseGet(() -> {
                        Skill created = new Skill();
                        created.setName(normalized);
                        return skillRepository.save(created);
                    });
            required.add(skill);
        }
        job.setRequiredSkills(required);
        jobRepository.save(job);
    }
}
