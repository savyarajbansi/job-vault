package com.project8.jobvault.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminRoleRequest(
        @NotBlank String role) {
}
