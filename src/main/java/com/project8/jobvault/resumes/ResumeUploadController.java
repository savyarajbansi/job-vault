package com.project8.jobvault.resumes;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.parsing.ParseErrorException;
import com.project8.jobvault.parsing.ParseResult;
import com.project8.jobvault.parsing.ResumeParseAttempt;
import com.project8.jobvault.parsing.ResumeParseAttemptRepository;
import com.project8.jobvault.parsing.ResumeParseAttemptStatus;
import com.project8.jobvault.parsing.ResumeParser;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import com.project8.jobvault.ratelimit.RateLimitService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seeker/resumes")
public class ResumeUploadController {
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ResumeMetadataRepository resumeMetadataRepository;
    private final UserAccountRepository userAccountRepository;
    private final ResumeStorageService storageService;
    private final ResumeParser resumeParser;
    private final Clock clock;
    private final DataSize maxFileSize;
    private final ObjectProvider<ResumeParseAttemptRepository> resumeParseAttemptRepositoryProvider;
    private final RateLimitService rateLimitService;

    public ResumeUploadController(
            ResumeMetadataRepository resumeMetadataRepository,
            UserAccountRepository userAccountRepository,
            ResumeStorageService storageService,
            ResumeParser resumeParser,
            Clock clock,
            ObjectProvider<ResumeParseAttemptRepository> resumeParseAttemptRepositoryProvider,
            RateLimitService rateLimitService,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.resumeMetadataRepository = resumeMetadataRepository;
        this.userAccountRepository = userAccountRepository;
        this.storageService = storageService;
        this.resumeParser = resumeParser;
        this.clock = clock;
        this.resumeParseAttemptRepositoryProvider = resumeParseAttemptRepositoryProvider;
        this.rateLimitService = rateLimitService;
        this.maxFileSize = maxFileSize;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeUploadResponse> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestPart("file") @NotNull MultipartFile file) {
        UserAccount seeker = requireUser(principal);
        rateLimitService.checkUpload(seeker.getId());
        String contentType = resolvePdfContentType(file);

        ResumeMetadata existing = resumeMetadataRepository.findBySeekerId(seeker.getId()).orElse(null);
        ResumeMetadata saved = existing;
        if (saved == null) {
            saved = new ResumeMetadata();
            saved.setSeeker(seeker);
            saved.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            saved.setContentType(contentType);
            saved.setFileSizeBytes(file.getSize());
            saved.setProcessingStatus(ResumeProcessingStatus.UPLOADED);
            saved = resumeMetadataRepository.save(saved);
        }

        long parseStart = 0L;
        boolean parseStarted = false;
        try {
            parseStart = System.nanoTime();
            parseStarted = true;
            ParseResult result = resumeParser.parse(file.getBytes());
            // Do not touch the current metadata or file until the replacement parses.
            String storageLocation = storageService.store(saved.getId(), file);
            saved.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            saved.setContentType(contentType);
            saved.setFileSizeBytes(file.getSize());
            saved.setStorageLocation(storageLocation);
            saved.setStorageType("LOCAL_DISK");
            saved.setStorageKey(storageLocation);
            saved.setParsedText(result.extractedText());
            saved.setInferredSkills(joinSkills(result.inferredSkills()));
            saved.setParsedAt(clock.instant());
            saved.setFailureCode(null);
            saved.setProcessingStatus(ResumeProcessingStatus.PARSED);
            saved = resumeMetadataRepository.save(saved);
            recordParseAttempt(saved, ResumeParseAttemptStatus.SUCCESS, null, parseStart, result);
        } catch (ParseErrorException ex) {
            recordParseAttempt(saved, ResumeParseAttemptStatus.FAILED, ex.getCode(), parseStart, null);
            if (existing == null) {
                saved.setProcessingStatus(ResumeProcessingStatus.FAILED);
                saved.setFailureCode(ex.getCode());
                resumeMetadataRepository.save(saved);
            }
            throw ex;
        } catch (IOException ex) {
            if (parseStarted) {
                recordParseAttempt(saved, ResumeParseAttemptStatus.FAILED, UploadErrorCodes.UPLOAD_FAILED, parseStart, null);
            }
            if (existing == null) {
                saved.setProcessingStatus(ResumeProcessingStatus.FAILED);
                saved.setFailureCode(UploadErrorCodes.UPLOAD_FAILED);
                resumeMetadataRepository.save(saved);
            }
            throw new UploadErrorException(
                    UploadErrorCodes.UPLOAD_FAILED,
                    UploadErrorCodes.MESSAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("reason", "storage_failed"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResumeUploadResponse(saved.getId(), saved.getProcessingStatus()));
    }

    private String resolvePdfContentType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UploadErrorException(
                    UploadErrorCodes.UPLOAD_FAILED,
                    UploadErrorCodes.MESSAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("reason", "empty_file"));
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new UploadErrorException(
                    UploadErrorCodes.FILE_TOO_LARGE,
                    UploadErrorCodes.MESSAGE_FILE_TOO_LARGE,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    Map.of("reason", "file_too_large"));
        }
        String contentType = normalizeContentType(file.getContentType());
        String filename = file.getOriginalFilename();
        boolean hasPdfType = contentType != null && contentType.equalsIgnoreCase(PDF_CONTENT_TYPE);
        boolean hasPdfName = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (!hasPdfType && !hasPdfName) {
            throw new UploadErrorException(
                    UploadErrorCodes.UNSUPPORTED_FILE,
                    UploadErrorCodes.MESSAGE_UNSUPPORTED_FILE,
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    Map.of("reason", "unsupported_type"));
        }
        if (hasPdfName) {
            return PDF_CONTENT_TYPE;
        }
        return contentType;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String trimmed = contentType.trim();
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

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume.pdf";
        }
        return filename.trim();
    }

    private String joinSkills(List<String> inferredSkills) {
        if (inferredSkills == null || inferredSkills.isEmpty()) {
            return "";
        }
        return inferredSkills.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void recordParseAttempt(
            ResumeMetadata resume,
            ResumeParseAttemptStatus status,
            String errorCode,
            long parseStartNanos,
            ParseResult result) {
        ResumeParseAttemptRepository repository = resumeParseAttemptRepositoryProvider.getIfAvailable();
        if (repository == null || resume == null) {
            return;
        }
        ResumeParseAttempt attempt = new ResumeParseAttempt();
        attempt.setResume(resume);
        attempt.setStatus(status);
        attempt.setErrorCode(errorCode);
        attempt.setDurationMs(toDurationMs(parseStartNanos));
        if (result != null) {
            String text = result.extractedText();
            List<String> skills = result.inferredSkills();
            attempt.setExtractedTextLength(text == null ? 0 : text.length());
            attempt.setInferredSkillCount(skills == null ? 0 : skills.size());
        }
        repository.save(attempt);
    }

    private int toDurationMs(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long millis = Duration.ofNanos(Math.max(0L, elapsedNanos)).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
