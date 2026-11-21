package com.careermatcher.profile;

import com.careermatcher.common.error.FieldError;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validation rules that bean-validation annotations cannot express. Pure and unit-testable. */
final class ProfileRules {

  private ProfileRules() {}

  static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
  static final Set<String> LINKEDIN_HOSTS = Set.of("linkedin.com");
  static final Set<String> GITHUB_HOSTS = Set.of("github.com");

  static void checkCountry(String code, List<FieldError> errors) {
    if (code != null && !ISO_COUNTRIES.contains(code)) {
      errors.add(new FieldError("countryCode", "Unknown country code"));
    }
  }

  static void checkCurrency(String code, List<FieldError> errors) {
    if (code == null) {
      return;
    }
    try {
      Currency.getInstance(code);
    } catch (IllegalArgumentException ex) {
      errors.add(new FieldError("salaryCurrency", "Unknown currency code"));
    }
  }

  /**
   * Accepts only absolute https URLs without credentials; optionally restricted to a host (or its
   * subdomains, e.g. {@code www.linkedin.com}).
   */
  static void checkUrl(String field, String value, Set<String> allowedHosts, List<FieldError> errors) {
    if (value == null) {
      return;
    }
    try {
      URI uri = new URI(value);
      String host = uri.getHost();
      boolean ok =
          "https".equalsIgnoreCase(uri.getScheme())
              && host != null
              && uri.getUserInfo() == null
              && (allowedHosts.isEmpty()
                  || allowedHosts.stream()
                      .anyMatch(
                          h ->
                              host.equalsIgnoreCase(h)
                                  || host.toLowerCase(Locale.ROOT).endsWith("." + h)));
      if (!ok) {
        errors.add(
            new FieldError(
                field,
                allowedHosts.isEmpty()
                    ? "Must be a valid https:// URL"
                    : "Must be an https:// URL on " + String.join(" or ", allowedHosts)));
      }
    } catch (URISyntaxException ex) {
      errors.add(new FieldError(field, "Must be a valid https:// URL"));
    }
  }
}
