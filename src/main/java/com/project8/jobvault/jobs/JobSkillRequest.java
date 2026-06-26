package com.project8.jobvault.jobs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobSkillRequest(
        @NotBlank @Size(max = 100) String name) {
}