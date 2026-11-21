package com.careermatcher.common.error;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException {

  private final long retryAfterSeconds;

  public RateLimitedException(long retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Try again later.");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
