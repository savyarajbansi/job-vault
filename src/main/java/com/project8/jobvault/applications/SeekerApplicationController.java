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

    @PostMapping("/{jobId}/apply")
    public ResponseEntity<JobApplicationResponse> apply(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount seeker = requireUser(principal);
        Job job = jobRepository.findByIdAndStatus(jobId, JobStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        Optional<JobApplication> existing = jobApplicationRepository.findByJobIdAndSeekerId(jobId, seeker.getId());
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application already exists");
        }
        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setSeeker(seeker);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(clock.instant());
        JobApplication saved = jobApplicationRepository.save(application);

        UserAccount employer = job.getEmployer();
        if (employer != null) {
            String applicant = displayNameOrEmail(seeker);
            notificationService.createNotification(
                    employer,
                    NotificationType.APPLICATION_SUBMITTED,
                    "New application for " + job.getTitle() + " from " + applicant);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
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
