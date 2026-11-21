package com.careermatcher.common.security;

import com.careermatcher.user.Role;
import java.util.UUID;

/**
 * The verified caller. Controllers obtain it via {@code @AuthenticationPrincipal}; the id comes
 * exclusively from a validated access token, never from request parameters.
 */
public record AuthenticatedUser(UUID id, Role role) {}
