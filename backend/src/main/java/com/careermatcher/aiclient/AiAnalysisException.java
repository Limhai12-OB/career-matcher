package com.careermatcher.aiclient;

import java.util.Set;

/** A failed analysis call. {@code code} is one of {@link #KNOWN_CODES}. */
public class AiAnalysisException extends RuntimeException {

  /** Codes the AI service may return, plus client-side ones. */
  public static final Set<String> KNOWN_CODES =
      Set.of(
          "UNSUPPORTED_FORMAT", "DOCUMENT_TOO_LARGE", "MALFORMED_DOCUMENT", "UNSAFE_DOCUMENT",
          "MACROS_NOT_ALLOWED", "ENCRYPTED_DOCUMENT", "TOO_MANY_PAGES", "NO_EXTRACTABLE_TEXT",
          "DOCUMENT_TOO_LONG", "EXTRACTION_TIMEOUT", "AI_NOT_CONFIGURED", "AI_UNAVAILABLE",
          "AI_RATE_LIMITED", "AI_TIMEOUT", "AI_REFUSED", "AI_INVALID_OUTPUT");

  private final String code;

  public AiAnalysisException(String code, String message) {
    super(message);
    this.code = KNOWN_CODES.contains(code) ? code : "AI_UNAVAILABLE";
  }

  public AiAnalysisException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = KNOWN_CODES.contains(code) ? code : "AI_UNAVAILABLE";
  }

  public String getCode() {
    return code;
  }
}
