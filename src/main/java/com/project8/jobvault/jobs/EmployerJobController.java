package com.project8.jobvault.jobs;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.matching.CorpusIdfService;
import com.project8.jobvault.skills.Skill;
import com.project8.jobvault.skills.SkillRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employer/jobs")
public class EmployerJobController {
    private final JobRepository jobRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectProvider<JobRequiredSkillSyncService> jobRequiredSkillSyncServiceProvider;
    private final ObjectProvider<SkillRepository> skillRepositoryProvider;
    private final CorpusIdfService corpusIdfService;
    private final Clock clock;

    public EmployerJobController(
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            ObjectProvider<JobRequiredSkillSyncService> jobRequiredSkillSyncServiceProvider,
            ObjectProvider<SkillRepository> skillRepositoryProvider,
            CorpusIdfService corpusIdfService,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.jobRequiredSkillSyncServiceProvider = jobRequiredSkillSyncServiceProvider;
        this.skillRepositoryProvider = skillRepositoryProvider;
        this.corpusIdfService = corpusIdfService;
        this.clock = clock;
    }

    @GetMapping
    public List<JobSummaryResponse> listOwnJobs(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount employer = requireUser(principal);
        return jobRepository.findAllByEmployerIdOrderByCreatedAtDesc(employer.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @PostMapping
    public ResponseEntity<JobDetailResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody JobCreateRequest request) {
        UserAccount employer = requireUser(principal);
        Objects.requireNonNull(request, "request");
        Job job = new Job();
        job.setEmployer(employer);
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setCompanyName(normalizeText(request.companyName()));
        job.setLocation(normalizeText(request.location()));
        job.setRemoteEligible(request.remoteEligible());
        job.setMinExperienceYears(request.minExperienceYears());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setEducationRequirement(request.educationRequirement());
        job.setStatus(JobStatus.DRAFT);
        Job saved = jobRepository.save(job);
        // Populate auto-detected skills immediately so a brand-new job isn't
        // sitting with an empty skill set until the employer happens to save again.
        syncRequiredSkills(saved);
        refreshIdfCorpus();
        Job withSkills = jobRepository.findById(saved.getId()).orElse(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(withSkills));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobDetailResponse> get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount employer = requireUser(principal);
        return findOwned(jobId, employer.getId())
                .map(job -> ResponseEntity.ok(toDetail(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{jobId}")
    public ResponseEntity<JobDetailResponse> update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobUpdateRequest request) {
        UserAccount employer = requireUser(principal);
        Objects.requireNonNull(request, "request");
        Optional<Job> ownedJob = findOwned(jobId, employer.getId());
        if (ownedJob.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Job job = ownedJob.get();
        if (job.getStatus() == JobStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is disabled");
        }
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setCompanyName(normalizeText(request.companyName()));
        job.setLocation(normalizeText(request.location()));
        job.setRemoteEligible(request.remoteEligible());
        job.setMinExperienceYears(request.minExperienceYears());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setEducationRequirement(request.educationRequirement());
        Job saved = jobRepository.save(job);
        syncRequiredSkills(saved);
        refreshIdfCorpus();
        Job withSkills = jobRepository.findById(saved.getId()).orElse(saved);
        return ResponseEntity.ok(toDetail(withSkills));
    }

    @PostMapping("/{jobId}/publish")
    public ResponseEntity<JobDetailResponse> publish(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount employer = requireUser(principal);
        int updated = jobRepository.transitionDraftToActive(jobId, employer.getId(), clock.instant());
        if (updated == 0) {
            throwNotFoundOrConflict(jobId, employer.getId(), "Job is not in DRAFT");
        }
        Job saved = findOwned(jobId, employer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        syncRequiredSkills(saved);
        refreshIdfCorpus();
        Job withSkills = findOwned(jobId, employer.getId()).orElse(saved);
        return ResponseEntity.ok(toDetail(withSkills));
    }

    @PostMapping("/{jobId}/disable")
    public ResponseEntity<JobDetailResponse> disable(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount employer = requireUser(principal);
        int updated = jobRepository.transitionActiveToDisabled(jobId, employer.getId(), clock.instant());
        if (updated == 0) {
            throwNotFoundOrConflict(jobId, employer.getId(), "Job is not ACTIVE");
        }
        Job saved = findOwned(jobId, employer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        syncRequiredSkills(saved);
        refreshIdfCorpus();
        Job withSkills = findOwned(jobId, employer.getId()).orElse(saved);
        return ResponseEntity.ok(toDetail(withSkills));
    }

    @PostMapping("/{jobId}/reactivate")
    public ResponseEntity<JobDetailResponse> reactivate(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount employer = requireUser(principal);
        int updated = jobRepository.transitionDisabledToActive(jobId, employer.getId(), clock.instant());
        if (updated == 0) {
            throwNotFoundOrConflict(jobId, employer.getId(), "Job cannot be reactivated");
        }
        Job saved = findOwned(jobId, employer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        refreshIdfCorpus();
        return ResponseEntity.ok(toDetail(saved));
    }

    @PostMapping("/{jobId}/skills")
    public ResponseEntity<JobDetailResponse> addRequiredSkill(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobSkillRequest request) {
        UserAccount employer = requireUser(principal);
        Objects.requireNonNull(request, "request");
        Job job = requireEditableOwnedJob(jobId, employer.getId());
        String normalized = request.name().trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill name is required");
        }
        SkillRepository skillRepository = requireSkillRepository();
        Skill skill = skillRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Skill created = new Skill();
                    created.setName(normalized);
                    return skillRepository.save(created);
                });
        job.getRequiredSkills().add(skill);
        Job saved = jobRepository.save(job);
        return ResponseEntity.ok(toDetail(saved));
    }

    @DeleteMapping("/{jobId}/skills/{skillName}")
    public ResponseEntity<JobDetailResponse> removeRequiredSkill(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @PathVariable String skillName) {
        UserAccount employer = requireUser(principal);
        Job job = requireEditableOwnedJob(jobId, employer.getId());
        String normalized = skillName == null ? "" : skillName.trim();
        job.getRequiredSkills().removeIf(skill ->
                skill.getName() != null && skill.getName().equalsIgnoreCase(normalized));
        Job saved = jobRepository.save(job);
        return ResponseEntity.ok(toDetail(saved));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Job requireEditableOwnedJob(UUID jobId, UUID employerId) {
        Job job = findOwned(jobId, employerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getStatus() == JobStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is disabled");
        }
        return job;
    }

    private SkillRepository requireSkillRepository() {
        SkillRepository repository = skillRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Skill catalog unavailable");
        }
        return repository;
    }

    private void throwNotFoundOrConflict(UUID jobId, UUID employerId, String conflictReason) {
        UUID resolvedJobId = Objects.requireNonNull(jobId, "jobId");
        Optional<Job> existing = jobRepository.findById(resolvedJobId);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = existing.get();
        if (job.getEmployer() == null || !Objects.equals(job.getEmployer().getId(), employerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, conflictReason);
    }

    private Optional<Job> findOwned(UUID jobId, UUID employerId) {
        if (jobId == null || employerId == null) {
            return Optional.empty();
        }
        return jobRepository.findById(jobId)
                .filter(job -> job.getEmployer() != null)
                .filter(job -> employerId.equals(job.getEmployer().getId()));
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        UUID userId = principal.userId();
        if (userId == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(userId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private void syncRequiredSkills(Job job) {
        jobRequiredSkillSyncServiceProvider.ifAvailable(service -> service.syncRequiredSkills(job));
    }

    private void refreshIdfCorpus() {
        corpusIdfService.rebuildFromRepository();
    }

    private JobSummaryResponse toSummary(Job job) {
        return new JobSummaryResponse(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getLocation(),
                job.getRemoteEligible(),
                job.getMinExperienceYears(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getStatus(),
                job.getCreatedAt());
    }

    private JobDetailResponse toDetail(Job job) {
        List<String> requiredSkills = job.getRequiredSkills() == null
                ? List.of()
                : job.getRequiredSkills().stream()
                        .map(Skill::getName)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList();
        return new JobDetailResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getCompanyName(),
                job.getLocation(),
                job.getRemoteEligible(),
                job.getMinExperienceYears(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getEducationRequirement(),
                requiredSkills,
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getPublishedAt(),
                job.getDisabledAt());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}