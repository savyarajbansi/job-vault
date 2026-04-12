package com.project8.jobvault.resumes;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeMetadataRepository extends JpaRepository<ResumeMetadata, UUID> {
}
