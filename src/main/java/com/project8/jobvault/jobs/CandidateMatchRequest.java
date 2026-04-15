package com.project8.jobvault.jobs;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CandidateMatchRequest(
        @NotNull UUID seekerId,
        @DecimalMin("0.0") @DecimalMax("100.0") double score) {
}
