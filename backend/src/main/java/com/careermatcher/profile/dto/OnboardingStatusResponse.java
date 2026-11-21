package com.careermatcher.profile.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param completed whether the candidate finished onboarding
 * @param missingRequired stable keys of required fields still missing
 */
public record OnboardingStatusResponse(
    boolean completed, Instant completedAt, List<String> missingRequired) {}
