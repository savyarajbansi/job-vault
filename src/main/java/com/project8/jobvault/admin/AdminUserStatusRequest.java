package com.project8.jobvault.admin;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(
        @NotNull Boolean enabled) {
}
