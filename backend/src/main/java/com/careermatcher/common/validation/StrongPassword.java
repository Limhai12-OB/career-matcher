package com.careermatcher.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Password policy: 10-128 characters, at least one letter and one digit. */
@NotNull
@Size(min = 10, max = 128)
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\p{Nd}).*$", flags = Pattern.Flag.DOTALL)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
  String message() default
      "Password must be 10-128 characters and include at least one letter and one number";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
