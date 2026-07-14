package com.project8.jobvault.matching;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import com.project8.jobvault.ratelimit.RateLimitService;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employer/jobs")
public class EmployerCandidateMatchController {
    private final UserAccountRepository userAccountRepository;
    private final ObjectProvider<MatchingFacade> matchingFacadeProvider;
    private final RateLimitService rateLimitService;

    public EmployerCandidateMatchController(
            UserAccountRepository userAccountRepository,
            ObjectProvider<MatchingFacade> matchingFacadeProvider,
            RateLimitService rateLimitService) {
        this.userAccountRepository = userAccountRepository;
        this.matchingFacadeProvider = matchingFacadeProvider;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/{jobId}/matches/candidates")
    public ResponseEntity<EmployerCandidateMatchResponse> employerCandidates(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UserAccount employer = requireUser(principal);
        MatchPagination.validate(limit, offset);
        rateLimitService.checkMatch(employer.getId());
        return ResponseEntity.ok(requireFacade().employerCandidates(employer.getId(), jobId, limit, offset));
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private MatchingFacade requireFacade() {
        MatchingFacade facade = matchingFacadeProvider.getIfAvailable();
        if (facade == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching service unavailable");
        }
        return facade;
    }
}
