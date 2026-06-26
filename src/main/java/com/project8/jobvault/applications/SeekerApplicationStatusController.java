package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import com.project8.jobvault.users.UserDisplayNames;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/seeker/applications")
public class SeekerApplicationStatusController {
    private final JobApplicationRepository jobApplicationRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public SeekerApplicationStatusController(
            JobApplicationRepository jobApplicationRepository,
            UserAccountRepository userAccountRepository,
            Clock clock) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SeekerApplicationResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount seeker = requireUser(principal);
        return jobApplicationRepository.findAllBySeekerIdOrderByCreatedAtDesc(seeker.getId()).stream()
                .map(this::toSeekerResponse)
                .toList();
    }

    @PatchMapping("/{applicationId}/withdraw")
    public ResponseEntity<JobApplicationResponse> withdraw(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID applicationId) {
        UserAccount seeker = requireUser(principal);

        int updated = jobApplicationRepository.transitionForSeeker(
                applicationId,
                seeker.getId(),
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.WITHDRAWN,
                null,
                clock.instant());
        if (updated == 0) {
            updated = jobApplicationRepository.transitionForSeeker(
                    applicationId,
                    seeker.getId(),
                    ApplicationStatus.UNDER_REVIEW,
                    ApplicationStatus.WITHDRAWN,
                    null,
                    clock.instant());
        }
        if (updated == 0) {
            throw resolveMissingOrConflict(applicationId, seeker.getId(), "Application cannot be withdrawn");
        }

        JobApplication updatedApplication = jobApplicationRepository.findByIdAndSeekerId(applicationId, seeker.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        return ResponseEntity.ok(toResponse(updatedApplication));
    }

    private ResponseStatusException resolveMissingOrConflict(UUID applicationId, UUID seekerId, String conflictReason) {
        UUID resolvedApplicationId = Objects.requireNonNull(applicationId, "applicationId");
        UUID resolvedSeekerId = Objects.requireNonNull(seekerId, "seekerId");
        Optional<JobApplication> existing = jobApplicationRepository.findById(resolvedApplicationId);
        if (existing.isEmpty()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        JobApplication application = existing.get();
        UUID existingSeekerId = application.getSeeker() == null ? null : application.getSeeker().getId();
        if (existingSeekerId == null || !resolvedSeekerId.equals(existingSeekerId)) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT, conflictReason);
    }

    private JobApplicationResponse toResponse(JobApplication application) {
        Job job = Objects.requireNonNull(application.getJob(), "job");
        UserAccount seeker = Objects.requireNonNull(application.getSeeker(), "seeker");
        UUID jobId = Objects.requireNonNull(job.getId(), "jobId");
        UUID seekerId = Objects.requireNonNull(seeker.getId(), "seekerId");
        return new JobApplicationResponse(
                application.getId(),
                jobId,
                seekerId,
                UserDisplayNames.nameOrEmail(seeker),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getDecidedAt());
    }

    private SeekerApplicationResponse toSeekerResponse(JobApplication application) {
        Job job = application.getJob();
        return new SeekerApplicationResponse(
                application.getId(),
                job == null ? null : job.getId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompanyName(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getDecidedAt());
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
}