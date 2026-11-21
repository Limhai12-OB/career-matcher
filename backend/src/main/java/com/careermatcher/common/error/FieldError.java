package com.careermatcher.common.error;

/** A single invalid input field, returned in the {@code errors} array of a 400 response. */
public record FieldError(String field, String message) {}
