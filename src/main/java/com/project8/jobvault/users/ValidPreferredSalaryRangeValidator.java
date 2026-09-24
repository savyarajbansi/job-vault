package com.project8.jobvault.users;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPreferredSalaryRangeValidator
        implements ConstraintValidator<ValidPreferredSalaryRange, SeekerProfileRequest> {

    @Override
    public boolean isValid(SeekerProfileRequest request, ConstraintValidatorContext context) {
        if (request == null || request.preferredSalaryMin() == null
                || request.preferredSalaryMax() == null) {
            return true;
        }
        if (request.preferredSalaryMax() >= request.preferredSalaryMin()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "preferredSalaryMax must be greater than or equal to preferredSalaryMin")
                .addPropertyNode("preferredSalaryMax")
                .addConstraintViolation();
        return false;
    }
}
