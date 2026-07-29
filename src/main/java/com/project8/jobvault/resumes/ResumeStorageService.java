package com.project8.jobvault.resumes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
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
        Path staged = root.resolve("." + resumeId + "." + UUID.randomUUID() + ".tmp");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
        return target.toString().replace("\\", "/");
    }

    public Resource load(ResumeMetadata metadata) throws IOException {
        String location = metadata.getStorageKey() == null
                ? metadata.getStorageLocation()
                : metadata.getStorageKey();
        if (location == null || location.isBlank()) {
            throw new IOException("Resume storage location is missing");
        }
        Path path = Path.of(location).normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("Resume file is missing");
        }
        return new InputStreamResource(Files.newInputStream(path));
    }
}
