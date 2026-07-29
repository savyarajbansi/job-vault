package com.project8.jobvault.users;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.jobs.JobRepository;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.matching.MatchingPreferences;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.resumes.ResumeMetadataRepository;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import com.project8.jobvault.resumes.ResumeStorageService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/seekers")
public class SeekerPublicProfileController {
    private final UserAccountRepository userAccountRepository;
    private final ResumeMetadataRepository resumeMetadataRepository;
    private final JobRepository jobRepository;
    private final ResumeStorageService resumeStorageService;

    public SeekerPublicProfileController(
            UserAccountRepository userAccountRepository,
            ResumeMetadataRepository resumeMetadataRepository,
            JobRepository jobRepository,
            ResumeStorageService resumeStorageService) {
        this.userAccountRepository = userAccountRepository;
        this.resumeMetadataRepository = resumeMetadataRepository;
        this.jobRepository = jobRepository;
        this.resumeStorageService = resumeStorageService;
    }

    @GetMapping("/{seekerId}")
    @Transactional(readOnly = true)
    public SeekerProfileResponse getProfile(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID seekerId,
            @RequestParam(required = false) UUID jobId) {
        UserAccount viewer = requireUser(principal);
        authorizeProfileView(viewer, seekerId, jobId);
        UserAccount seeker = userAccountRepository.findById(seekerId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seeker not found"));
        ResumeMetadata resume = resumeMetadataRepository.findBySeekerId(seekerId).orElse(null);
        return toResponse(seeker, resume);
    }

    @GetMapping("/{seekerId}/resume")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> resume(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID seekerId,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(defaultValue = "false") boolean download) {
        UserAccount viewer = requireUser(principal);
        authorizeProfileView(viewer, seekerId, jobId);
        ResumeMetadata resume = resumeMetadataRepository.findBySeekerId(seekerId)
                .filter(item -> item.getProcessingStatus() == ResumeProcessingStatus.PARSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed resume not found"));
        try {
            Resource resource = resumeStorageService.load(resume);
            String filename = resume.getOriginalFilename() == null || resume.getOriginalFilename().isBlank()
                    ? "resume.pdf"
                    : resume.getOriginalFilename();
            ContentDisposition disposition = download
                    ? ContentDisposition.attachment().filename(filename).build()
                    : ContentDisposition.inline().filename(filename).build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(disposition);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found", ex);
        }
    }

    private void authorizeProfileView(UserAccount viewer, UUID seekerId, UUID jobId) {
        if (viewer.getId().equals(seekerId)) {
            return;
        }
        boolean employer = viewer.getRoles().stream()
                .anyMatch(role -> "EMPLOYER".equals(role.getName()));
        if (!employer || jobId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        Job job = jobRepository.findById(jobId)
                .filter(candidateJob -> candidateJob.getStatus() == JobStatus.ACTIVE)
                .filter(candidateJob -> candidateJob.getEmployer() != null && viewer.getId().equals(candidateJob.getEmployer().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        UserAccount seeker = userAccountRepository.findById(seekerId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        resumeMetadataRepository.findBySeekerId(seekerId)
                .filter(resume -> resume.getProcessingStatus() == ResumeProcessingStatus.PARSED)
                .filter(resume -> MatchingPreferences.sectorMatches(seeker.getPreferredSectors(), job.getSectorTags()))
                .filter(resume -> MatchingPreferences.workModeMatches(seeker.getWorkMode(), job.getWorkMode()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    private SeekerProfileResponse toResponse(UserAccount user, ResumeMetadata resume) {
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

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findWithRolesById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }
}
