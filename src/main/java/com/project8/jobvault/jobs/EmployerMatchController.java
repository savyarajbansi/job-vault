package com.project8.jobvault.jobs;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employer/jobs")
public class EmployerMatchController {
    private static final double MATCH_THRESHOLD = 70.0;

    private final JobRepository jobRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    public EmployerMatchController(
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/{jobId}/matches")
    public ResponseEntity<CandidateMatchResponse> recordMatch(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody CandidateMatchRequest request) {
        UserAccount employer = requireUser(principal);
        if (jobId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId is required");
        }
        UUID resolvedJobId = Objects.requireNonNull(jobId, "jobId");
        Job job = jobRepository.findById(resolvedJobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getEmployer() == null || !employer.getId().equals(job.getEmployer().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not ACTIVE");
        }
        UUID requestSeekerId = request.seekerId();
        if (requestSeekerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seekerId is required");
        }
        UUID seekerId = Objects.requireNonNull(requestSeekerId, "seekerId");
        UserAccount seeker = userAccountRepository.findById(seekerId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seeker not found"));

        boolean notified = false;
        if (request.score() >= MATCH_THRESHOLD) {
            String candidate = displayNameOrEmail(seeker);
            notificationService.createNotification(
                    employer,
                    NotificationType.JOB_MATCH_FOUND,
                    "New candidate match for " + job.getTitle() + ": " + candidate + " (" + request.score() + "%)");
            notified = true;
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new CandidateMatchResponse(notified));
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
