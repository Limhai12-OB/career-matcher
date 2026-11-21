package com.careermatcher.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Career preferences; primary key is the owning user's id (1:1). */
@Entity
@Table(name = "candidate_preferences")
class CandidatePreferences {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "desired_roles", nullable = false, columnDefinition = "text[]")
  private List<String> desiredRoles = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "preferred_locations", nullable = false, columnDefinition = "text[]")
  private List<String> preferredLocations = new ArrayList<>();

  /** Stored as enum names; see {@link EmploymentType}. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "employment_types", nullable = false, columnDefinition = "text[]")
  private List<String> employmentTypes = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "remote_preference", length = 20)
  private RemotePreference remotePreference;

  @Column(name = "willing_to_relocate", nullable = false)
  private boolean willingToRelocate;

  @Column(name = "expected_min_salary")
  private Integer expectedMinSalary;

  @Column(name = "salary_currency", length = 3)
  private String salaryCurrency;

  @Column(name = "available_from")
  private LocalDate availableFrom;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected CandidatePreferences() {}

  CandidatePreferences(UUID userId) {
    this.userId = userId;
  }

  void update(
      List<String> desiredRoles,
      List<String> preferredLocations,
      List<String> employmentTypes,
      RemotePreference remotePreference,
      boolean willingToRelocate,
      Integer expectedMinSalary,
      String salaryCurrency,
      LocalDate availableFrom) {
    this.desiredRoles = new ArrayList<>(desiredRoles);
    this.preferredLocations = new ArrayList<>(preferredLocations);
    this.employmentTypes = new ArrayList<>(employmentTypes);
    this.remotePreference = remotePreference;
    this.willingToRelocate = willingToRelocate;
    this.expectedMinSalary = expectedMinSalary;
    this.salaryCurrency = salaryCurrency;
    this.availableFrom = availableFrom;
  }

  UUID getUserId() { return userId; }
  List<String> getDesiredRoles() { return List.copyOf(desiredRoles); }
  List<String> getPreferredLocations() { return List.copyOf(preferredLocations); }
  List<String> getEmploymentTypes() { return List.copyOf(employmentTypes); }
  RemotePreference getRemotePreference() { return remotePreference; }
  boolean isWillingToRelocate() { return willingToRelocate; }
  Integer getExpectedMinSalary() { return expectedMinSalary; }
  String getSalaryCurrency() { return salaryCurrency; }
  LocalDate getAvailableFrom() { return availableFrom; }
  Instant getUpdatedAt() { return updatedAt; }
}
