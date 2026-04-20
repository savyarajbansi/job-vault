package com.project8.jobvault.admin;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobDetailResponse;
import com.project8.jobvault.jobs.JobModerationAction;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobModerationController {
    private final JobRepository jobRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public AdminJobModerationController(
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @PostMapping("/{jobId}/approve")
    @Transactional
    public ResponseEntity<JobDetailResponse> approve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId) {
        UserAccount admin = requireUser(principal);
        Instant now = clock.instant();
        int rows = jobRepository.approveForAdmin(jobId, admin, now, now);
        if (rows == 0) {
            throw resolveMissingOrConflict(jobId);
        }
        Job updated = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return ResponseEntity.ok(toDetail(updated));
    }

    @PostMapping("/{jobId}/reject")
    @Transactional
    public ResponseEntity<JobDetailResponse> reject(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody AdminJobModerationRequest request) {
        return moderateToDisabled(principal, jobId, request, JobModerationAction.REJECTED);
    }

    @PostMapping("/{jobId}/disable")
    @Transactional
    public ResponseEntity<JobDetailResponse> disable(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody AdminJobModerationRequest request) {
        return moderateToDisabled(principal, jobId, request, JobModerationAction.DISABLED);
    }

    private ResponseEntity<JobDetailResponse> moderateToDisabled(
            JwtPrincipal principal,
            UUID jobId,
            AdminJobModerationRequest request,
            JobModerationAction action) {
        UserAccount admin = requireUser(principal);
        Instant now = clock.instant();
        String reason = request.moderationReason().trim();
        int rows = jobRepository.moderateActiveToDisabled(jobId, action, reason, admin, now, now);
        if (rows == 0) {
            throw resolveMissingOrConflict(jobId);
        }
        Job updated = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return ResponseEntity.ok(toDetail(updated));
    }

    private ResponseStatusException resolveMissingOrConflict(UUID jobId) {
        Optional<Job> existing = jobRepository.findById(jobId);
        if (existing.isEmpty()) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = existing.get();
        if (job.getEmployer() == null || job.getEmployer().getId() == null) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT, "Invalid moderation transition");
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private JobDetailResponse toDetail(Job job) {
        return new JobDetailResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getPublishedAt(),
                job.getDisabledAt());
    }
}
