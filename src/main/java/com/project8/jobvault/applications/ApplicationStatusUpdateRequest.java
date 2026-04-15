package com.project8.jobvault.applications;

import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(@NotNull ApplicationStatus status) {
}
