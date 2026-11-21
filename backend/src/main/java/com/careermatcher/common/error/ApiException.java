package com.careermatcher.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base type for expected business errors. The {@code code} is a stable, machine-readable
 * identifier that clients may rely on; the message must be safe to show to end users.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final Map<String, Object> properties;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public ApiException(
      HttpStatus status, String code, String message, Map<String, Object> properties) {
    super(message);
    this.status = status;
    this.code = code;
    this.properties = Map.copyOf(properties);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  /** Extra, client-safe fields added to the problem response. */
  public Map<String, Object> getProperties() {
    return properties;
  }
}
