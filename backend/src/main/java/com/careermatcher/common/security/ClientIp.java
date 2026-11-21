package com.careermatcher.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client address for rate limiting. Uses the servlet remote address, which honours
 * X-Forwarded-For only when {@code server.forward-headers-strategy} is enabled (set it when, and
 * only when, the backend runs behind a trusted reverse proxy).
 */
public final class ClientIp {

  private ClientIp() {}

  public static String of(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
