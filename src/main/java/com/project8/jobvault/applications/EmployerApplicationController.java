package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employer/applications")
public class EmployerApplicationController {
    private static final Set<ApplicationStatus> EMPLOYER_STATUSES = EnumSet.of(
            ApplicationStatus.UNDER_REVIEW,
            ApplicationStatus.REJECTED,
            ApplicationStatus.ACCEPTED);

    private final JobApplicationRepository jobApplicationRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public EmployerApplicationController(
            JobApplicationRepository jobApplicationRepository,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService,
            Clock clock) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<JobApplicationResponse> updateStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        UserAccount employer = requireUser(principal);
        ApplicationStatus newStatus = request.status();
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

        UserAccount seeker = saved.getSeeker();
        if (seeker != null) {
            String statusLabel = newStatus.name().replace('_', ' ').toLowerCase();
            notificationService.createNotification(
                    seeker,
                    NotificationType.APPLICATION_STATUS_CHANGED,
                    "Your application for " + saved.getJob().getTitle() + " is now " + statusLabel);
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    private ResponseStatusException resolveMissingOrConflict(
            UUID applicationId,
            UUID employerId,
            String conflictReason) {
        Optional<JobApplication> existing = jobApplicationRepository.findById(applicationId);
        if (existing.isEmpty()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        JobApplication application = existing.get();
        if (application.getJob() == null
                || application.getJob().getEmployer() == null
                || !employerId.equals(application.getJob().getEmployer().getId())) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT, conflictReason);
    }

    private JobApplicationResponse toResponse(JobApplication application) {
        return new JobApplicationResponse(
                application.getId(),
                application.getJob().getId(),
                application.getSeeker().getId(),
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
