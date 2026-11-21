package com.careermatcher.auth.dto;

import com.careermatcher.user.Role;
import java.time.Instant;
import java.util.UUID;

public record UserSummary(
    UUID id, String email, Role role, boolean onboardingCompleted, Instant createdAt) {}
