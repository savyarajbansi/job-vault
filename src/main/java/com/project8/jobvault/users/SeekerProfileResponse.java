package com.project8.jobvault.users;

import java.util.UUID;

public record SeekerProfileResponse(
        UUID userId,
        String preferredSector,
        String preferredLocation,
        Boolean remoteOk,
        Integer yearsExperience) {
}
