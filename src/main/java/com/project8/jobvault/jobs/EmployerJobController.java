package com.project8.jobvault.jobs;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final Clock clock;

    public EmployerJobController(
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<JobDetailResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody JobCreateRequest request) {
        UserAccount employer = requireUser(principal);
        Job job = new Job();
        job.setEmployer(employer);
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setStatus(JobStatus.DRAFT);
        Job saved = jobRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(saved));
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
        Job saved = jobRepository.save(job);
        return ResponseEntity.ok(toDetail(saved));
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
        return ResponseEntity.ok(toDetail(saved));
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
        return ResponseEntity.ok(toDetail(saved));
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
        return ResponseEntity.ok(toDetail(saved));
    }

    private void throwNotFoundOrConflict(UUID jobId, UUID employerId, String conflictReason) {
        Optional<Job> existing = jobRepository.findById(jobId);
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
        UUID resolvedJobId = Objects.requireNonNull(jobId, "jobId");
        UUID resolvedEmployerId = Objects.requireNonNull(employerId, "employerId");
        return jobRepository.findById(resolvedJobId)
                .filter(job -> job.getEmployer() != null)
                .filter(job -> resolvedEmployerId.equals(job.getEmployer().getId()));
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

    private JobDetailResponse toDetail(Job job) {
        Instant createdAt = job.getCreatedAt();
        Instant updatedAt = job.getUpdatedAt();
        return new JobDetailResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getStatus(),
                createdAt,
                updatedAt,
                job.getPublishedAt(),
                job.getDisabledAt());
    }
}
