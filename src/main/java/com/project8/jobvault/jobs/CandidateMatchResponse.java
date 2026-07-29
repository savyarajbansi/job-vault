package com.project8.jobvault.jobs;

import java.util.UUID;

public record CandidateMatchResponse(
        boolean notified,
        UUID shortlistId,
        CandidateMatchStatus status) {
}
