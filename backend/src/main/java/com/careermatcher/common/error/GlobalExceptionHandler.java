package com.careermatcher.common.error;

import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized error handling producing RFC 7807 {@code application/problem+json} responses. Every
 * problem carries a stable {@code code}. Framework exceptions (malformed JSON, 404, 405, ...) are
 * handled by the superclass.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setProperty("code", ex.getCode());
    ex.getProperties().forEach(problem::setProperty);
    ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
    if (ex instanceof RateLimitedException rl) {
      response.header(HttpHeaders.RETRY_AFTER, String.valueOf(rl.getRetryAfterSeconds()));
    }
    return response.body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .sorted(Comparator.comparing(FieldError::field))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
    problem.setProperty("code", "VALIDATION_FAILED");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
        problem.setProperty("code", defaultCode(status));
      }
    }
    return response;
  }

  /** Constraint violations on request parameters / path variables (@Validated controllers). */
  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  ProblemDetail handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
    List<FieldError> errors =
        ex.getConstraintViolations().stream()
            .map(
                v -> {
                  String path = v.getPropertyPath().toString();
                  String field = path.substring(path.lastIndexOf('.') + 1);
                  return new FieldError(field, v.getMessage());
                })
            .sorted(Comparator.comparing(FieldError::field))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more parameters are invalid.");
    problem.setProperty("code", "VALIDATION_FAILED");
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The resource was modified concurrently. Please retry.");
    problem.setProperty("code", "CONCURRENT_MODIFICATION");
    return problem;
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    // Do not echo constraint details (may contain user data).
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The request conflicts with existing data.");
    problem.setProperty("code", "DATA_CONFLICT");
    return problem;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    problem.setProperty("code", "INTERNAL_ERROR");
    return problem;
  }

  private static String defaultCode(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "BAD_REQUEST";
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 406 -> "NOT_ACCEPTABLE";
      case 413 -> "PAYLOAD_TOO_LARGE";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      default -> "HTTP_" + status.value();
    };
  }
}
