package com.careermatcher.common.validation;

/** Regular expressions shared by request DTO validation. */
public final class TextRules {

  private TextRules() {}

  /** Single-line text: no control characters at all. */
  public static final String SINGLE_LINE = "^[^\\p{Cntrl}]*$";

  /** Multi-line text: no control characters except newline, carriage return and tab. */
  public static final String MULTI_LINE = "^(?:[^\\p{Cntrl}]|[\\n\\r\\t])*$";

  /** Trims and converts blank strings to {@code null}. */
  public static String clean(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
