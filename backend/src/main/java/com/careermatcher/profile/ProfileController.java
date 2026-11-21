package com.careermatcher.profile;

import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.profile.dto.OnboardingStatusResponse;
import com.careermatcher.profile.dto.PreferencesResponse;
import com.careermatcher.profile.dto.ProfileResponse;
import com.careermatcher.profile.dto.UpdatePreferencesRequest;
import com.careermatcher.profile.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current candidate's own profile. All routes are under {@code /me}: the owner is always the
 * authenticated user, so one user can never read or modify another user's profile.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile", description = "Candidate profile, career preferences and onboarding")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

  private final ProfileService service;

  public ProfileController(ProfileService service) {
    this.service = service;
  }

  @Operation(summary = "Get my profile")
  @GetMapping("/profile")
  public ProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.getProfile(user.id());
  }

  @Operation(summary = "Replace my profile")
  @PutMapping("/profile")
  public ProfileResponse updateProfile(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody UpdateProfileRequest request) {
    return service.updateProfile(user.id(), request);
  }

  @Operation(summary = "Get my career preferences")
  @GetMapping("/preferences")
  public PreferencesResponse getPreferences(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.getPreferences(user.id());
  }

  @Operation(summary = "Replace my career preferences")
  @PutMapping("/preferences")
  public PreferencesResponse updatePreferences(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody UpdatePreferencesRequest request) {
    return service.updatePreferences(user.id(), request);
  }

  @Operation(summary = "Get my onboarding status")
  @GetMapping("/onboarding")
  public OnboardingStatusResponse getOnboarding(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.getOnboardingStatus(user.id());
  }

  @Operation(
      summary = "Complete onboarding",
      description = "Fails with 422 ONBOARDING_INCOMPLETE listing missingRequired fields.")
  @PostMapping("/onboarding/complete")
  public OnboardingStatusResponse completeOnboarding(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return service.completeOnboarding(user.id());
  }
}
