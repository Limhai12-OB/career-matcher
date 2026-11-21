package com.careermatcher.job;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.Seniority;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable search filters. All user input is bound as parameters (no string-built SQL). */
final class JobSpecifications {

  private JobSpecifications() {}

  /** AND of the given specifications, ignoring absent (null) filters. */
  @SafeVarargs
  static Specification<Job> all(Specification<Job>... specs) {
    return java.util.Arrays.stream(specs)
        .filter(java.util.Objects::nonNull)
        .reduce(Specification::and)
        .orElse(Specification.unrestricted());
  }

  static Specification<Job> visibleOn(LocalDate today) {
    return (root, q, cb) ->
        cb.and(
            cb.equal(root.get("status"), JobStatus.PUBLISHED),
            cb.or(cb.isNull(root.get("expiresOn")), cb.greaterThanOrEqualTo(root.get("expiresOn"), today)));
  }

  static Specification<Job> text(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    String like = "%" + escape(query.strip().toLowerCase(Locale.ROOT)) + "%";
    return (root, q, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("title")), like, '\\'),
            cb.like(cb.lower(root.get("companyName")), like, '\\'),
            cb.like(cb.lower(root.get("description")), like, '\\'));
  }

  static <T> Specification<Job> equal(String attribute, T value) {
    return value == null ? null : (root, q, cb) -> cb.equal(root.get(attribute), value);
  }

  static Specification<Job> status(JobStatus status) {
    return equal("status", status);
  }

  static Specification<Job> origin(DataOrigin origin) {
    return equal("dataOrigin", origin);
  }

  static Specification<Job> remote(RemoteType remote) {
    return equal("remoteType", remote);
  }

  static Specification<Job> employment(EmploymentType type) {
    return equal("employmentType", type);
  }

  static Specification<Job> seniority(Seniority seniority) {
    return equal("seniority", seniority);
  }

  static Specification<Job> country(String country) {
    return equal("countryCode", country);
  }

  /** Jobs that list any of the given catalog skills. */
  static Specification<Job> anySkill(Collection<UUID> skillIds) {
    if (skillIds == null || skillIds.isEmpty()) {
      return null;
    }
    return (root, q, cb) -> {
      Subquery<UUID> sub = q.subquery(UUID.class);
      Root<Job> j = sub.from(Job.class);
      Join<Job, JobSkillRequirement> s = j.join("skills");
      sub.select(j.get("id")).where(cb.equal(j.get("id"), root.get("id")), s.get("skillId").in(skillIds));
      return cb.exists(sub);
    };
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
