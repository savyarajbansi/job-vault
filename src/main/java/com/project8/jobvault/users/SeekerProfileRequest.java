package com.project8.jobvault.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SeekerProfileRequest(
        String preferredSector,
        String preferredLocation,
        Boolean remoteOk,
        @Min(0) @Max(60) Integer yearsExperience) {
}
