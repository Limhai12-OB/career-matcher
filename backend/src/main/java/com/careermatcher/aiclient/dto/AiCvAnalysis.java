package com.careermatcher.aiclient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Response of the AI service's CV analysis. Every field is re-validated here before anything is
 * persisted: the AI service is trusted, but the model output it carries is not.
 */
public record AiCvAnalysis(
    @NotBlank @Pattern(regexp = "cv-analysis-v1") String schemaVersion,
    @NotBlank @Size(max = 40) String provider,
    @NotBlank @Size(max = 80) String model,
    @NotBlank @Size(max = 40) String promptVersion,
    @NotNull @Valid Document document,
    @NotNull @Size(max = 150) List<@NotNull @Valid Skill> skills,
    @NotNull @Size(max = 10) List<@NotNull @Valid Education> education,
    @NotNull @Size(max = 20) List<@NotNull @Valid Experience> experience,
    @NotNull @Size(max = 20) List<@NotNull @Valid Project> projects,
    @NotNull @Size(max = 20) List<@NotNull @Valid Certification> certifications,
    @NotNull @Valid DroppedItems droppedItems,
    @NotNull @Size(max = 10) List<@NotNull Warning> warnings) {

  static final String DATE = "^\\d{4}(-(0[1-9]|1[0-2]))?$";

  public enum DocType { PDF, DOCX }

  public enum SkillCategory {
    PROGRAMMING_LANGUAGE, FRAMEWORK_LIBRARY, DATABASE, CLOUD_DEVOPS, TOOL, METHODOLOGY, SOFT_SKILL, OTHER
  }

  public enum EducationLevel {
    HIGH_SCHOOL, VOCATIONAL, ASSOCIATE, BACHELOR, MASTER, DOCTORATE, BOOTCAMP, OTHER
  }

  public enum ExperienceType {
    FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT, FREELANCE, VOLUNTEER, OTHER
  }

  public enum Warning {
    INSTRUCTION_LIKE_TEXT_REMOVED,
    MODEL_FLAGGED_SUSPICIOUS_CONTENT,
    UNVERIFIED_ITEMS_REMOVED,
    LIMITED_EXTRACTION
  }

  public record Document(
      @NotNull DocType type, @Min(1) @Max(1000) Integer pages, @Min(0) @Max(1_000_000) int characters) {}

  public record Skill(
      @NotBlank @Size(max = 80) String name,
      @NotNull SkillCategory category,
      @NotBlank @Size(max = 300) String evidence) {}

  public record Education(
      @NotBlank @Size(max = 200) String institution,
      @Size(max = 200) String degree,
      @Size(max = 200) String fieldOfStudy,
      @NotNull EducationLevel level,
      @Pattern(regexp = DATE) String startDate,
      @Pattern(regexp = DATE) String endDate,
      boolean ongoing,
      @NotBlank @Size(max = 300) String evidence) {}

  public record Experience(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 200) String organization,
      @NotNull ExperienceType employmentType,
      @Pattern(regexp = DATE) String startDate,
      @Pattern(regexp = DATE) String endDate,
      boolean isCurrent,
      @NotNull @Size(max = 30) List<@NotBlank @Size(max = 80) String> skills,
      @NotBlank @Size(max = 300) String evidence) {}

  public record Project(
      @NotBlank @Size(max = 200) String name,
      @NotNull @Size(max = 30) List<@NotBlank @Size(max = 80) String> skills,
      @NotBlank @Size(max = 300) String evidence) {}

  public record Certification(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 200) String issuer,
      @Pattern(regexp = "^\\d{4}$") String year,
      @NotBlank @Size(max = 300) String evidence) {}

  public record DroppedItems(
      @Min(0) int skills,
      @Min(0) int education,
      @Min(0) int experience,
      @Min(0) int projects,
      @Min(0) int certifications) {}
}
