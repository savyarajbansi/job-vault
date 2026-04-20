package com.project8.jobvault.applications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/seeker/jobs")
public class SeekerApplicationController {
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public SeekerApplicationController(
            JobRepository jobRepository,
            JobApplicationRepository jobApplicationRepository,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @PostMapping("/{jobId}/draft")
    public ResponseEntity<JobApplicationResponse> draft(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount seeker = requireUser(principal);
        Job job = requireActiveJob(jobId);
        Optional<JobApplication> existing = jobApplicationRepository.findByJobIdAndSeekerId(jobId, seeker.getId());
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application already exists");
        }

        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setSeeker(seeker);
        application.setStatus(ApplicationStatus.DRAFT);
        JobApplication saved = saveOrConflict(application);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/{jobId}/apply")
    public ResponseEntity<JobApplicationResponse> apply(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount seeker = requireUser(principal);
        Job job = requireActiveJob(jobId);
        Optional<JobApplication> existing = jobApplicationRepository.findByJobIdAndSeekerId(jobId, seeker.getId());
        if (existing.isPresent() && existing.get().getStatus() != ApplicationStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application already exists");
        }
        JobApplication saved;
        if (existing.isPresent()) {
            int updated = jobApplicationRepository.transitionDraftToSubmitted(jobId, seeker.getId(), clock.instant());
            if (updated == 0) {
                throw resolveMissingOrConflictForSeeker(
                        existing.get().getId(), seeker.getId(), "Application cannot be submitted");
            }
            saved = jobApplicationRepository.findByJobIdAndSeekerId(jobId, seeker.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        } else {
            JobApplication application = new JobApplication();
            application.setJob(job);
            application.setSeeker(seeker);
            application.setStatus(ApplicationStatus.SUBMITTED);
            application.setSubmittedAt(clock.instant());
            saved = saveOrConflict(application);
        }

        UserAccount employer = job.getEmployer();
        if (employer != null) {
            String applicant = displayNameOrEmail(seeker);
            notificationService.createNotification(
                    employer,
                    NotificationType.APPLICATION_SUBMITTED,
                    "New application for " + job.getTitle() + " from " + applicant);
        }

        HttpStatus responseStatus = existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(responseStatus).body(toResponse(saved));
    }

    private Job requireActiveJob(UUID jobId) {
        return jobRepository.findByIdAndStatus(jobId, JobStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private JobApplication saveOrConflict(JobApplication application) {
        try {
            return jobApplicationRepository.save(application);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application already exists");
        }
    }

    private ResponseStatusException resolveMissingOrConflictForSeeker(
            UUID applicationId,
            UUID seekerId,
            String conflictReason) {
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

    private String displayNameOrEmail(UserAccount user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail();
    }
}
