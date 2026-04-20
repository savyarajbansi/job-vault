package com.project8.jobvault.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminJobModerationRequest(
        @NotBlank String moderationReason) {
}
