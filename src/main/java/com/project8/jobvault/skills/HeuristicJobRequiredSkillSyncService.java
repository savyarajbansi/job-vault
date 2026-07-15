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

/**
 * Detects skills mentioned in a job's title or description and adds them to
 * the job's required-skills set.
 *
 * <p>This is intentionally additive-only: it never removes a skill, whether
 * that skill was previously auto-detected or added manually by the employer
 * (see {@code EmployerJobController#addRequiredSkill}). That makes it safe to
 * run automatically on every save (create/update/publish/disable) without
 * ever clobbering a manually curated list. Known trade-off: manually removing
 * a skill that is still literally present in the posting text will not "stick"
 * past the next save, since detection re-derives from the current text every
 * time.
 */
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
        if (job == null || job.getId() == null) {
            return;
        }
        // Re-fetch within this service's own transaction rather than trusting the
        // caller's entity state, so this works correctly regardless of which
        // transaction (if any) the caller is in.
        Job managed = jobRepository.findById(job.getId()).orElse(null);
        if (managed == null) {
            return;
        }
        String title = managed.getTitle() == null ? "" : managed.getTitle();
        String description = managed.getDescription() == null ? "" : managed.getDescription();
        String postingText = title + "\n" + description;
        if (postingText.isBlank()) {
            return;
        }
        Set<Skill> required = managed.getRequiredSkills();
        if (required == null) {
            required = new LinkedHashSet<>();
            managed.setRequiredSkills(required);
        }
        for (String inferred : skillCatalog.extractSkills(postingText)) {
            String normalized = skillCatalog.canonicalize(inferred);
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
        jobRepository.save(managed);
    }
}
