package com.project8.jobvault.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ValidSalaryRange
public record JobCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @Size(max = 200) String companyName,
        @Size(max = 150) String location,
        Boolean remoteEligible,
        @Min(0) @Max(60) Integer minExperienceYears,
        @Min(0) Integer salaryMin,
        @Min(0) Integer salaryMax,
        EducationRequirement educationRequirement) {
}
