package com.project8.jobvault.jobs;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidSalaryRangeValidator implements ConstraintValidator<ValidSalaryRange, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof JobCreateRequest request) {
            return isSalaryRangeValid(request.salaryMin(), request.salaryMax(), context);
        }
        if (value instanceof JobUpdateRequest request) {
            return isSalaryRangeValid(request.salaryMin(), request.salaryMax(), context);
        }
        return true;
    }

    private boolean isSalaryRangeValid(Integer salaryMin, Integer salaryMax,
            ConstraintValidatorContext context) {
        if (salaryMin == null || salaryMax == null) {
            // Partial salary is allowed; the DB constraint also handles null cases.
            return true;
        }
        if (salaryMax < salaryMin) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "salaryMax must be greater than or equal to salaryMin")
                    .addPropertyNode("salaryMax")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
