package com.careermatcher.user;

import java.util.UUID;

/** Minimal view used to validate access tokens on each request. */
public record UserTokenState(UUID id, Role role, UserStatus status, int tokenVersion) {}
