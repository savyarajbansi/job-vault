package com.project8.jobvault.resumes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeStorageService {
    private final Path root;

    public ResumeStorageService(
            @Value("${jobvault.storage.resumes-dir:storage/resumes}") String resumesDir) {
        this.root = Path.of(resumesDir).normalize();
    }

    public String store(UUID resumeId, MultipartFile file) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve(resumeId + ".pdf");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString().replace("\\", "/");
    }
}
