package com.project8.jobvault.resumes;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seeker/resumes")
public class ResumeUploadController {
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ResumeMetadataRepository resumeMetadataRepository;
    private final UserAccountRepository userAccountRepository;
    private final ResumeStorageService storageService;
    private final DataSize maxFileSize;

    public ResumeUploadController(
            ResumeMetadataRepository resumeMetadataRepository,
            UserAccountRepository userAccountRepository,
            ResumeStorageService storageService,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.resumeMetadataRepository = resumeMetadataRepository;
        this.userAccountRepository = userAccountRepository;
        this.storageService = storageService;
        this.maxFileSize = maxFileSize;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeUploadResponse> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestPart("file") @NotNull MultipartFile file) {
        UserAccount seeker = requireUser(principal);
        validatePdf(file);

        ResumeMetadata metadata = new ResumeMetadata();
        metadata.setSeeker(seeker);
        metadata.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        metadata.setContentType(file.getContentType());
        metadata.setFileSizeBytes(file.getSize());
        metadata.setProcessingStatus(ResumeProcessingStatus.UPLOADED);
        ResumeMetadata saved = resumeMetadataRepository.save(metadata);

        try {
            String storageLocation = storageService.store(saved.getId(), file);
            saved.setStorageLocation(storageLocation);
            resumeMetadataRepository.save(saved);
        } catch (IOException ex) {
            saved.setProcessingStatus(ResumeProcessingStatus.FAILED);
            saved.setFailureCode(UploadErrorCodes.UPLOAD_FAILED);
            resumeMetadataRepository.save(saved);
            throw new UploadErrorException(
                    UploadErrorCodes.UPLOAD_FAILED,
                    UploadErrorCodes.MESSAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("reason", "storage_failed"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResumeUploadResponse(saved.getId(), saved.getProcessingStatus()));
    }

    private void validatePdf(MultipartFile file) {
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
        String contentType = file.getContentType();
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
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume.pdf";
        }
        return filename.trim();
    }
}
