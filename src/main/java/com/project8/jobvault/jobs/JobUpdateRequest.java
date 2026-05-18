package com.project8.jobvault.jobs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record JobUpdateRequest(
        @NotBlank String title,
        @NotBlank String description,
        String location,
        Boolean remoteEligible,
        @Min(0) @Max(60) Integer minExperienceYears) {
}
