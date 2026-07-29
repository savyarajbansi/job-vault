package com.project8.jobvault.users;

import com.project8.jobvault.matching.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SeekerProfileRequest(
        @Size(max = 200) String displayName,
        List<String> preferredSectors,
        @Size(max = 150) String preferredLocation,
        WorkMode workMode,
        @Min(0) @Max(60) Integer yearsExperience,
        List<String> skills) {
}
