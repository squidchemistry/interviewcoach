package com.aicoach.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/** The value must be a multiple of 0.5 (e.g. years of experience: 0, 0.5, 1, 1.5 ...). Null is valid. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HalfStep.Validator.class)
public @interface HalfStep {

    String message() default "must be a multiple of 0.5";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HalfStep, BigDecimal> {
        private static final BigDecimal TWO = BigDecimal.valueOf(2);

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            BigDecimal doubled = value.multiply(TWO);
            return doubled.signum() == 0 || doubled.stripTrailingZeros().scale() <= 0;
        }
    }
}
