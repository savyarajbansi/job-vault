package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
        Optional<JobApplication> existing = jobApplicationRepository.findById(applicationId);
        if (existing.isEmpty()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        JobApplication application = existing.get();
        if (application.getSeeker() == null || !seekerId.equals(application.getSeeker().getId())) {
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
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }
}
