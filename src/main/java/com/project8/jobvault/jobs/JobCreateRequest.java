package com.project8.jobvault.jobs;

import jakarta.validation.constraints.NotBlank;

public record JobCreateRequest(
        @NotBlank String title,
        @NotBlank String description) {
}
