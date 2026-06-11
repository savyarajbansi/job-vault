package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employer")
public class EmployerApplicationController {
    private static final Set<ApplicationStatus> EMPLOYER_STATUSES = EnumSet.of(
            ApplicationStatus.UNDER_REVIEW,
            ApplicationStatus.REJECTED,
            ApplicationStatus.ACCEPTED);

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public EmployerApplicationController(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService,
            Clock clock) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<List<JobApplicationResponse>> listApplications(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount employer = requireUser(principal);
        // Verify the job exists and belongs to this employer before returning applications.
        // Returns 404 rather than an empty list when the job is missing or owned by someone else,
        // so the caller can distinguish "no applications yet" from "job not found".
        boolean jobOwnedByEmployer = jobRepository.findById(jobId)
                .filter(job -> job.getEmployer() != null)
                .filter(job -> employer.getId().equals(job.getEmployer().getId()))
                .isPresent();
        if (!jobOwnedByEmployer) {
            return ResponseEntity.notFound().build();
        }
        List<JobApplicationResponse> responses =
                jobApplicationRepository
                        .findAllByJobIdAndJobEmployerIdOrderBySubmittedAtDesc(jobId, employer.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/applications/{applicationId}/status")
    public ResponseEntity<JobApplicationResponse> updateStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        UserAccount employer = requireUser(principal);
        Objects.requireNonNull(request, "request");
        ApplicationStatus newStatus = Objects.requireNonNull(request.status(), "status");
        if (!EMPLOYER_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status change");
        }
        ApplicationStatus expectedStatus = newStatus == ApplicationStatus.UNDER_REVIEW
                ? ApplicationStatus.SUBMITTED
                : ApplicationStatus.UNDER_REVIEW;
        Instant now = clock.instant();
        Instant reviewedAt = newStatus == ApplicationStatus.UNDER_REVIEW ? now : null;
        Instant decidedAt = newStatus == ApplicationStatus.UNDER_REVIEW ? null : now;

        int updated = jobApplicationRepository.transitionForEmployer(
                applicationId,
                employer.getId(),
                expectedStatus,
                newStatus,
                reviewedAt,
                decidedAt);
        if (updated == 0) {
            throw resolveMissingOrConflict(applicationId, employer.getId(), "Invalid application transition");
        }

        JobApplication saved = jobApplicationRepository.findByIdAndJobEmployerId(applicationId, employer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        Job job = Objects.requireNonNull(saved.getJob(), "job");
        UserAccount seeker = saved.getSeeker();
        if (seeker != null) {
            String statusLabel = newStatus.name().replace('_', ' ').toLowerCase();
            notificationService.createNotification(
                    seeker,
                    NotificationType.APPLICATION_STATUS_CHANGED,
                    "Your application for " + job.getTitle() + " is now " + statusLabel);
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    private ResponseStatusException resolveMissingOrConflict(
            UUID applicationId,
            UUID employerId,
            String conflictReason) {
        UUID resolvedApplicationId = Objects.requireNonNull(applicationId, "applicationId");
        UUID resolvedEmployerId = Objects.requireNonNull(employerId, "employerId");
        Optional<JobApplication> existing = jobApplicationRepository.findById(resolvedApplicationId);
        if (existing.isEmpty()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        JobApplication application = existing.get();
        if (application.getJob() == null
                || application.getJob().getEmployer() == null
                || !resolvedEmployerId.equals(application.getJob().getEmployer().getId())) {
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