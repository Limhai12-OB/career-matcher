package com.careermatcher.common.security;

import com.careermatcher.common.config.CorsProperties;
import com.careermatcher.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * CSRF defence-in-depth for cookie-authenticated endpoints (refresh, logout): if the browser sent
 * an {@code Origin} header it must be one of the configured frontend origins. Non-browser clients
 * that send no Origin are allowed, since CSRF requires a browser (which always sends Origin on
 * cross-origin POSTs).
 */
@Component
public class OriginVerifier {

  private final CorsProperties cors;

  public OriginVerifier(CorsProperties cors) {
    this.cors = cors;
  }

  public void verify(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !cors.allowedOrigins().contains(origin)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED", "Origin not allowed.");
    }
  }
}
