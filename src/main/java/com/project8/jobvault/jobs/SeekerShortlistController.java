package com.project8.jobvault.jobs;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.notifications.NotificationService;
import com.project8.jobvault.notifications.NotificationType;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/seeker/shortlists")
public class SeekerShortlistController {
    private final ObjectProvider<CandidateMatchNotificationRepository> repositoryProvider;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    public SeekerShortlistController(
            ObjectProvider<CandidateMatchNotificationRepository> repositoryProvider,
            UserAccountRepository userAccountRepository,
            NotificationService notificationService) {
        this.repositoryProvider = repositoryProvider;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/{shortlistId}/accept")
    @Transactional
    public ResponseEntity<CandidateMatchResponse> accept(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID shortlistId) {
        UserAccount seeker = requireUser(principal);
        CandidateMatchNotificationRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shortlist not found");
        }
        CandidateMatchNotification shortlist = repository.findByIdAndSeekerId(shortlistId, seeker.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shortlist not found"));
        if (shortlist.getStatus() == CandidateMatchStatus.ACCEPTED) {
            return ResponseEntity.ok(new CandidateMatchResponse(false, shortlist.getId(), shortlist.getStatus()));
        }
        shortlist.setStatus(CandidateMatchStatus.ACCEPTED);
        CandidateMatchNotification saved = repository.save(shortlist);
        if (saved.getEmployer() != null && saved.getJob() != null) {
            notificationService.createNotification(
                    saved.getEmployer(),
                    NotificationType.SHORTLIST_ACCEPTED,
                    "A candidate accepted your shortlist for " + saved.getJob().getTitle() + ".");
        }
        return ResponseEntity.ok(new CandidateMatchResponse(true, saved.getId(), saved.getStatus()));
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
