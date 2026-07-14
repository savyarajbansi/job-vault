package com.project8.jobvault.jobs;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CandidateMatchRequest(
        @NotNull UUID seekerId) {
}
