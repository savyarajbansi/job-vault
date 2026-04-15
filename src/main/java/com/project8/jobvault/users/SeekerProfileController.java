package com.project8.jobvault.users;

import com.project8.jobvault.auth.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seeker/profile")
public class SeekerProfileController {
    private final UserAccountRepository userAccountRepository;

    public SeekerProfileController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public SeekerProfileResponse getProfile(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount user = requireUser(principal);
        return toResponse(user);
    }

    @PatchMapping
    public ResponseEntity<SeekerProfileResponse> updateProfile(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody SeekerProfileRequest request) {
        UserAccount user = requireUser(principal);
        user.setPreferredSector(normalizeSector(request.preferredSector()));
        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(toResponse(saved));
    }

    private SeekerProfileResponse toResponse(UserAccount user) {
        return new SeekerProfileResponse(user.getId(), user.getPreferredSector());
    }

    private String normalizeSector(String sector) {
        if (sector == null) {
            return null;
        }
        String trimmed = sector.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
