package com.project8.jobvault.jobs;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.matching.MatchingFacade;
import com.project8.jobvault.matching.ScoredMatch;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/employer/jobs")
public class EmployerMatchController {
    private static final double MATCH_THRESHOLD = 0.70;

    private final JobRepository jobRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final ObjectProvider<MatchingFacade> matchingFacadeProvider;
    private final ObjectProvider<CandidateMatchNotificationRepository> notificationRecordProvider;

    public EmployerMatchController(
            JobRepository jobRepository,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService,
            ObjectProvider<MatchingFacade> matchingFacadeProvider,
            ObjectProvider<CandidateMatchNotificationRepository> notificationRecordProvider) {
        this.jobRepository = jobRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
        this.matchingFacadeProvider = matchingFacadeProvider;
        this.notificationRecordProvider = notificationRecordProvider;
    }

    @PostMapping("/{jobId}/matches")
    @Transactional
    public ResponseEntity<CandidateMatchResponse> recordMatch(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody CandidateMatchRequest request) {
        UserAccount employer = requireUser(principal);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getEmployer() == null || !employer.getId().equals(job.getEmployer().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not ACTIVE");
        }
        UUID seekerId = request.seekerId();
        UserAccount seeker = userAccountRepository.findById(seekerId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seeker not found"));

        MatchingFacade matchingFacade = matchingFacadeProvider.getIfAvailable();
        if (matchingFacade == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching service unavailable");
        }
        ScoredMatch scored = matchingFacade.scoreCurrentCandidate(jobId, seekerId);
        if (scored.overallScore() < MATCH_THRESHOLD) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new CandidateMatchResponse(false));
        }

        CandidateMatchNotificationRepository notificationRecords = notificationRecordProvider.getIfAvailable();
        if (notificationRecords != null) {
            if (notificationRecords.existsByJobIdAndSeekerId(jobId, seekerId)) {
                return ResponseEntity.status(HttpStatus.CREATED).body(new CandidateMatchResponse(false));
            }
            CandidateMatchNotification record = new CandidateMatchNotification();
            record.setJob(job);
            record.setSeeker(seeker);
            record.setEmployer(employer);
            record.setScore(scored.overallScore());
            try {
                notificationRecords.save(record);
            } catch (DataIntegrityViolationException ex) {
                return ResponseEntity.status(HttpStatus.CREATED).body(new CandidateMatchResponse(false));
            }
        }
        notificationService.createNotification(
                employer,
                NotificationType.JOB_MATCH_FOUND,
                "New candidate match for " + job.getTitle() + " (" + Math.round(scored.overallScore() * 100) + "%)");

        return ResponseEntity.status(HttpStatus.CREATED).body(new CandidateMatchResponse(true));
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
