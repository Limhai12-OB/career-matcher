package com.careermatcher.common.error;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Validation failures detected in service code (beyond bean-validation annotations). */
public class FieldValidationException extends ApiException {

  public FieldValidationException(List<FieldError> errors) {
    super(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "One or more fields are invalid.",
        Map.of("errors", List.copyOf(errors)));
  }

  public FieldValidationException(String field, String message) {
    this(List.of(new FieldError(field, message)));
  }
}
