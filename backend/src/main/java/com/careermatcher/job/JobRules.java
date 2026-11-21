package com.careermatcher.job;

import static com.careermatcher.common.validation.TextRules.clean;

import com.careermatcher.common.error.FieldError;
import com.careermatcher.common.error.FieldValidationException;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.dto.JobRequest;
import com.careermatcher.skill.SkillNormalizer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Business validation for job listings. Most importantly it keeps demonstration data and real
 * listings strictly apart: demo listings may only link to reserved example domains, and real
 * listings may not.
 */
@Component
public class JobRules {

  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
  private static final Pattern RESERVED_HOST = Pattern.compile("^(?:[a-z0-9-]+\\.)*example\\.(?:com|org|net)$");

  private final SkillNormalizer catalog;
  private final Clock clock;

  public JobRules(SkillNormalizer catalog, Clock clock) {
    this.catalog = catalog;
    this.clock = clock;
  }

  public Job.Content toContent(JobRequest r) {
    List<FieldError> errors = new ArrayList<>();
    String applicationUrl = clean(r.applicationUrl());
    String sourceUrl = clean(r.sourceUrl());
    String website = clean(r.companyWebsite());

    checkUrl("applicationUrl", applicationUrl, true, r.dataOrigin(), errors);
    checkUrl("sourceUrl", sourceUrl, false, r.dataOrigin(), errors);
    checkUrl("companyWebsite", website, false, r.dataOrigin(), errors);

    if (r.countryCode() != null && !COUNTRIES.contains(r.countryCode())) {
      errors.add(new FieldError("countryCode", "Unknown country code"));
    }
    int minExp = r.minExperienceMonths() == null ? 0 : r.minExperienceMonths();
    if (r.maxExperienceMonths() != null && r.maxExperienceMonths() < minExp) {
      errors.add(new FieldError("maxExperienceMonths", "Must be at least the minimum experience"));
    }

    boolean anySalary = r.salaryMin() != null || r.salaryMax() != null;
    if (anySalary && (r.salaryCurrency() == null || r.salaryPeriod() == null)) {
      errors.add(new FieldError("salaryCurrency", "Salary needs a currency and a period"));
    }
    if (!anySalary && (r.salaryCurrency() != null || r.salaryPeriod() != null)) {
      errors.add(new FieldError("salaryMin", "Enter a salary amount or clear the currency and period"));
    }
    if (r.salaryMin() != null && r.salaryMax() != null && r.salaryMax() < r.salaryMin()) {
      errors.add(new FieldError("salaryMax", "Maximum must be at least the minimum"));
    }
    if (r.salaryCurrency() != null) {
      try {
        Currency.getInstance(r.salaryCurrency());
      } catch (IllegalArgumentException e) {
        errors.add(new FieldError("salaryCurrency", "Unknown currency code"));
      }
    }

    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    if (r.postedOn() != null && r.postedOn().isAfter(today.plusDays(1))) {
      errors.add(new FieldError("postedOn", "Posting date can't be in the future"));
    }
    if (r.expiresOn() != null && r.postedOn() != null && r.expiresOn().isBefore(r.postedOn())) {
      errors.add(new FieldError("expiresOn", "Must be on or after the posting date"));
    }

    Set<JobSkillRequirement> skills = new LinkedHashSet<>();
    Set<UUID> seen = new HashSet<>();
    boolean anyRequired = false;
    for (JobRequest.SkillItem s : r.skills()) {
      if (!seen.add(s.skillId())) {
        errors.add(new FieldError("skills", "Each skill can only be listed once"));
        break;
      }
      if (catalog.byId(s.skillId()).isEmpty()) {
        errors.add(new FieldError("skills", "Unknown skill"));
        break;
      }
      anyRequired |= s.requirement() == JobEnums.SkillRequirement.REQUIRED;
      skills.add(new JobSkillRequirement(s.skillId(), s.requirement()));
    }
    if (!anyRequired) {
      errors.add(new FieldError("skills", "Mark at least one skill as required"));
    }

    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }
    List<String> fields =
        r.educationFields() == null
            ? List.of()
            : r.educationFields().stream().map(String::strip).filter(f -> !f.isEmpty()).distinct().toList();
    return new Job.Content(
        clean(r.title()),
        clean(r.companyName()),
        website,
        r.description().strip(),
        clean(r.city()),
        r.countryCode(),
        r.remoteType(),
        r.employmentType(),
        r.seniority(),
        minExp,
        r.maxExperienceMonths(),
        r.educationLevel(),
        fields,
        r.salaryMin(),
        r.salaryMax(),
        anySalary ? r.salaryCurrency() : null,
        anySalary ? r.salaryPeriod() : null,
        applicationUrl,
        clean(r.sourceName()),
        sourceUrl,
        r.dataOrigin(),
        r.postedOn(),
        r.expiresOn(),
        skills);
  }

  /** https only, no credentials; demo listings must use reserved example domains, real ones must not. */
  static void checkUrl(String field, String value, boolean required, DataOrigin origin, List<FieldError> errors) {
    if (value == null) {
      if (required) {
        errors.add(new FieldError(field, "URL is required"));
      }
      return;
    }
    String host;
    try {
      URI uri = new URI(value);
      host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) {
        errors.add(new FieldError(field, "Must be a valid https:// URL"));
        return;
      }
    } catch (URISyntaxException e) {
      errors.add(new FieldError(field, "Must be a valid https:// URL"));
      return;
    }
    boolean reserved = RESERVED_HOST.matcher(host.toLowerCase(Locale.ROOT)).matches();
    if (Objects.equals(field, "applicationUrl") || Objects.equals(field, "sourceUrl")) {
      if (origin == DataOrigin.DEMO && !reserved) {
        errors.add(new FieldError(field, "Demo listings must link to example.com/.org/.net, never to a real site"));
      } else if (origin == DataOrigin.REAL && reserved) {
        errors.add(new FieldError(field, "Real listings must link to the employer's or job board's actual page"));
      }
    }
  }
}
