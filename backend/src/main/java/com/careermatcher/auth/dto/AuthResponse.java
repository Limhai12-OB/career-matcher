package com.careermatcher.auth.dto;

/**
 * Returned by register, login, refresh and password change. The refresh token is never in the
 * body; it is set as an HttpOnly cookie.
 */
public record AuthResponse(
    String accessToken, String tokenType, long expiresIn, UserSummary user) {}
