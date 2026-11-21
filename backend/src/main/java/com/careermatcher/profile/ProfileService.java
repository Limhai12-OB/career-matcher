package com.careermatcher.profile;

import static com.careermatcher.common.validation.TextRules.clean;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.error.FieldError;
import com.careermatcher.common.error.FieldValidationException;
import com.careermatcher.profile.dto.OnboardingStatusResponse;
import com.careermatcher.profile.dto.PreferencesResponse;
import com.careermatcher.profile.dto.ProfileResponse;
import com.careermatcher.profile.dto.UpdatePreferencesRequest;
import com.careermatcher.profile.dto.UpdateProfileRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate profile, preferences and onboarding. Every method takes the owner's user id, which
 * callers must take from the authenticated principal — there is no way to address another user's
 * data through this API.
 */
@Service
public class ProfileService {

  private final CandidateProfileRepository profiles;
  private final CandidatePreferencesRepository preferences;
  private final Clock clock;

  public ProfileService(
      CandidateProfileRepository profiles, CandidatePreferencesRepository preferences, Clock clock) {
    this.profiles = profiles;
    this.preferences = preferences;
    this.clock = clock;
  }

  /** Creates the empty profile and preferences for a newly registered user. */
  @Transactional
  public void createInitialProfile(UUID userId, String fullName) {
    profiles.save(new CandidateProfile(userId, clean(fullName)));
    preferences.save(new CandidatePreferences(userId));
  }

  @Transactional(readOnly = true)
  public boolean isOnboardingCompleted(UUID userId) {
    return profiles.findById(userId).map(p -> p.getOnboardingCompletedAt() != null).orElse(false);
  }

  @Transactional(readOnly = true)
  public ProfileResponse getProfile(UUID userId) {
    return toResponse(profile(userId), prefs(userId));
  }

  @Transactional
  public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
    String linkedin = clean(req.linkedinUrl());
    String github = clean(req.githubUrl());
    String portfolio = clean(req.portfolioUrl());
    List<FieldError> errors = new ArrayList<>();
    ProfileRules.checkCountry(req.countryCode(), errors);
    ProfileRules.checkUrl("linkedinUrl", linkedin, ProfileRules.LINKEDIN_HOSTS, errors);
    ProfileRules.checkUrl("githubUrl", github, ProfileRules.GITHUB_HOSTS, errors);
    ProfileRules.checkUrl("portfolioUrl", portfolio, java.util.Set.of(), errors);
    int maxGraduationYear = Year.now(clock).getValue() + 8;
    if (req.graduationYear() != null && req.graduationYear() > maxGraduationYear) {
      errors.add(new FieldError("graduationYear", "Graduation year is too far in the future"));
    }
    if (clean(req.fullName()) == null || clean(req.fullName()).length() < 2) {
      errors.add(new FieldError("fullName", "Full name must be 2-120 characters"));
    }
    throwIfAny(errors);

    CandidateProfile p = profile(userId);
    p.update(
        clean(req.fullName()),
        clean(req.headline()),
        clean(req.bio()),
        clean(req.city()),
        req.countryCode(),
        req.careerStage(),
        req.graduationYear(),
        linkedin,
        github,
        portfolio);
    profiles.saveAndFlush(p);
    return toResponse(p, prefs(userId));
  }

  @Transactional(readOnly = true)
  public PreferencesResponse getPreferences(UUID userId) {
    return toResponse(prefs(userId));
  }

  @Transactional
  public PreferencesResponse updatePreferences(UUID userId, UpdatePreferencesRequest req) {
    List<FieldError> errors = new ArrayList<>();
    String currency = req.salaryCurrency();
    ProfileRules.checkCurrency(currency, errors);
    if ((req.expectedMinSalary() == null) != (currency == null)) {
      errors.add(
          new FieldError("salaryCurrency", "Provide both a minimum salary and a currency, or neither"));
    }
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    if (req.availableFrom() != null && req.availableFrom().isAfter(today.plusYears(2))) {
      errors.add(new FieldError("availableFrom", "Must be within the next two years"));
    }
    throwIfAny(errors);

    List<String> employmentTypes =
        req.employmentTypes() == null
            ? List.of()
            : req.employmentTypes().stream().sorted().map(Enum::name).toList();
    CandidatePreferences prefs = prefs(userId);
    prefs.update(
        dedupe(req.desiredRoles()),
        dedupe(req.preferredLocations()),
        employmentTypes,
        req.remotePreference(),
        Boolean.TRUE.equals(req.willingToRelocate()),
        req.expectedMinSalary(),
        currency,
        req.availableFrom());
    preferences.saveAndFlush(prefs);
    return toResponse(prefs);
  }

  @Transactional(readOnly = true)
  public OnboardingStatusResponse getOnboardingStatus(UUID userId) {
    CandidateProfile p = profile(userId);
    return new OnboardingStatusResponse(
        p.getOnboardingCompletedAt() != null,
        p.getOnboardingCompletedAt(),
        ProfileCompleteness.missingRequired(p, prefs(userId)));
  }

  /** Marks onboarding complete once all required information is present. Idempotent. */
  @Transactional
  public OnboardingStatusResponse completeOnboarding(UUID userId) {
    CandidateProfile p = profile(userId);
    List<String> missing = ProfileCompleteness.missingRequired(p, prefs(userId));
    if (!missing.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "ONBOARDING_INCOMPLETE",
          "Some required information is missing.",
          Map.of("missingRequired", missing));
    }
    p.markOnboardingCompleted(clock.instant());
    profiles.saveAndFlush(p);
    return new OnboardingStatusResponse(true, p.getOnboardingCompletedAt(), List.of());
  }

  // ---- helpers ----

  private CandidateProfile profile(UUID userId) {
    return profiles.findById(userId).orElseThrow(ProfileService::notFound);
  }

  private CandidatePreferences prefs(UUID userId) {
    return preferences.findById(userId).orElseThrow(ProfileService::notFound);
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found.");
  }

  private static void throwIfAny(List<FieldError> errors) {
    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }
  }

  /** Trims, drops blanks and removes case-insensitive duplicates, keeping first occurrence. */
  static List<String> dedupe(Collection<String> values) {
    if (values == null) {
      return List.of();
    }
    Map<String, String> unique = new LinkedHashMap<>();
    values.stream()
        .map(com.careermatcher.common.validation.TextRules::clean)
        .filter(Objects::nonNull)
        .forEach(v -> unique.putIfAbsent(v.toLowerCase(Locale.ROOT), v));
    return unique.entrySet().stream().map(Entry::getValue).toList();
  }

  private static ProfileResponse toResponse(CandidateProfile p, CandidatePreferences prefs) {
    return new ProfileResponse(
        p.getFullName(),
        p.getHeadline(),
        p.getBio(),
        p.getCity(),
        p.getCountryCode(),
        p.getCareerStage(),
        p.getGraduationYear(),
        p.getLinkedinUrl(),
        p.getGithubUrl(),
        p.getPortfolioUrl(),
        ProfileCompleteness.compute(p, prefs),
        p.getUpdatedAt());
  }

  private static PreferencesResponse toResponse(CandidatePreferences prefs) {
    return new PreferencesResponse(
        prefs.getDesiredRoles(),
        prefs.getPreferredLocations(),
        prefs.getEmploymentTypes().stream().map(EmploymentType::valueOf).toList(),
        prefs.getRemotePreference(),
        prefs.isWillingToRelocate(),
        prefs.getExpectedMinSalary(),
        prefs.getSalaryCurrency(),
        prefs.getAvailableFrom(),
        prefs.getUpdatedAt());
  }
}
