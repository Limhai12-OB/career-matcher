package com.careermatcher.common.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Writes fixed RFC 7807 bodies from the security filter chain, before MVC is involved. */
final class ProblemJsonWriter {

  private ProblemJsonWriter() {}

  static void write(HttpServletResponse response, int status, String title, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Values are compile-time constants, so no JSON escaping is needed.
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\"}");
  }
}
