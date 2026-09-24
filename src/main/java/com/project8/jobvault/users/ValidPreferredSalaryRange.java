package com.project8.jobvault.users;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidPreferredSalaryRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPreferredSalaryRange {
    String message() default "preferredSalaryMax must be greater than or equal to preferredSalaryMin";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
