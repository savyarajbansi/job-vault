package com.project8.jobvault.jobs;

import jakarta.validation.constraints.NotBlank;

public record JobUpdateRequest(
        @NotBlank String title,
        @NotBlank String description) {
}
