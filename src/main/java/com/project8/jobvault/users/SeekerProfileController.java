package com.project8.jobvault.users;

import com.project8.jobvault.matching.MatchingPreferences;
import com.project8.jobvault.matching.SectorCode;
import com.project8.jobvault.parsing.SkillCatalog;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.auth.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/seeker/profile")
public class SeekerProfileController {
    private final UserAccountRepository userAccountRepository;
    private final ResumeMetadataRepository resumeMetadataRepository;
    private final SkillCatalog skillCatalog;

    public SeekerProfileController(
            UserAccountRepository userAccountRepository,
            ResumeMetadataRepository resumeMetadataRepository,
            SkillCatalog skillCatalog) {
        this.userAccountRepository = userAccountRepository;
        this.resumeMetadataRepository = resumeMetadataRepository;
        this.skillCatalog = skillCatalog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public SeekerProfileResponse getProfile(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount user = requireUser(principal);
        return toResponse(user, resumeMetadataRepository.findBySeekerId(user.getId()).orElse(null));
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<SeekerProfileResponse> updateProfile(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody SeekerProfileRequest request) {
        UserAccount user = requireUser(principal);
        Objects.requireNonNull(request, "request");
        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setPreferredSectors(normalizeSectors(request.preferredSectors()));
        user.setPreferredLocation(normalizeText(request.preferredLocation()));
        user.setWorkMode(request.workMode());
        user.setYearsExperience(request.yearsExperience());

        ResumeMetadata resume = resumeMetadataRepository.findBySeekerId(user.getId()).orElse(null);
        if (resume != null && request.skills() != null) {
            resume.setInferredSkills(String.join(",", normalizeSkills(request.skills())));
            resumeMetadataRepository.save(resume);
        }

        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(toResponse(saved, resume));
    }

    public SeekerProfileResponse toResponse(UserAccount user, ResumeMetadata resume) {
        SeekerProfileResponse.CurrentResume current = resume == null ? null : new SeekerProfileResponse.CurrentResume(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getProcessingStatus(),
                resume.getParsedAt(),
                splitSkills(resume.getInferredSkills()));
        return new SeekerProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                MatchingPreferences.parseSectors(user.getPreferredSectors()),
                user.getPreferredLocation(),
                user.getWorkMode(),
                user.getYearsExperience(),
                current);
    }

    private String normalizeSectors(List<String> sectors) {
        if (sectors == null || sectors.isEmpty()) {
            return null;
        }
        List<String> normalized = sectors.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        for (String sector : normalized) {
            try {
                SectorCode.valueOf(sector);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sector: " + sector);
            }
        }
        return MatchingPreferences.joinSectors(normalized);
    }

    private List<String> normalizeSkills(List<String> skills) {
        return skills.stream()
                .filter(Objects::nonNull)
                .map(skillCatalog::canonicalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> splitSkills(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private String normalizeDisplayName(String value) {
        return normalizeText(value);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
