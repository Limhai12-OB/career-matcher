package com.careermatcher.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Candidate profile; primary key is the owning user's id (1:1). */
@Entity
@Table(name = "candidate_profiles")
class CandidateProfile {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Column(length = 160)
  private String headline;

  @Column(length = 2000)
  private String bio;

  @Column(length = 100)
  private String city;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "career_stage", length = 30)
  private CareerStage careerStage;

  @Column(name = "graduation_year")
  private Integer graduationYear;

  @Column(name = "linkedin_url", length = 300)
  private String linkedinUrl;

  @Column(name = "github_url", length = 300)
  private String githubUrl;

  @Column(name = "portfolio_url", length = 300)
  private String portfolioUrl;

  @Column(name = "onboarding_completed_at")
  private Instant onboardingCompletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected CandidateProfile() {}

  CandidateProfile(UUID userId, String fullName) {
    this.userId = userId;
    this.fullName = fullName;
  }

  void update(
      String fullName,
      String headline,
      String bio,
      String city,
      String countryCode,
      CareerStage careerStage,
      Integer graduationYear,
      String linkedinUrl,
      String githubUrl,
      String portfolioUrl) {
    this.fullName = fullName;
    this.headline = headline;
    this.bio = bio;
    this.city = city;
    this.countryCode = countryCode;
    this.careerStage = careerStage;
    this.graduationYear = graduationYear;
    this.linkedinUrl = linkedinUrl;
    this.githubUrl = githubUrl;
    this.portfolioUrl = portfolioUrl;
  }

  void markOnboardingCompleted(Instant at) {
    if (onboardingCompletedAt == null) {
      onboardingCompletedAt = at;
    }
  }

  UUID getUserId() { return userId; }
  String getFullName() { return fullName; }
  String getHeadline() { return headline; }
  String getBio() { return bio; }
  String getCity() { return city; }
  String getCountryCode() { return countryCode; }
  CareerStage getCareerStage() { return careerStage; }
  Integer getGraduationYear() { return graduationYear; }
  String getLinkedinUrl() { return linkedinUrl; }
  String getGithubUrl() { return githubUrl; }
  String getPortfolioUrl() { return portfolioUrl; }
  Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
  Instant getUpdatedAt() { return updatedAt; }
}
