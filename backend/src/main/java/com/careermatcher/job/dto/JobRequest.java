package com.careermatcher.job.dto;

import com.careermatcher.common.validation.TextRules;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EducationLevel;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.SalaryPeriod;
import com.careermatcher.job.JobEnums.Seniority;
import com.careermatcher.job.JobEnums.SkillRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Create/replace a job listing (admin). */
public record JobRequest(
    @NotBlank(message = "Title is required") @Size(max = 150)
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters") String title,
    @NotBlank(message = "Company is required") @Size(max = 120)
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters") String companyName,
    @Size(max = 500) String companyWebsite,
    @NotBlank(message = "Description is required")
        @Size(min = 50, max = 20000, message = "Description must be 50-20000 characters")
        @Pattern(regexp = TextRules.MULTI_LINE, message = "Contains invalid characters") String description,
    @Size(max = 100) @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters") String city,
    @Pattern(regexp = "^[A-Z]{2}$", message = "Use a two-letter ISO country code") String countryCode,
    @NotNull(message = "Work arrangement is required") RemoteType remoteType,
    @NotNull(message = "Employment type is required") EmploymentType employmentType,
    @NotNull(message = "Seniority is required") Seniority seniority,
    @Min(0) @Max(240) Integer minExperienceMonths,
    @Min(0) @Max(240) Integer maxExperienceMonths,
    EducationLevel educationLevel,
    @Size(max = 10) List<@NotBlank @Size(max = 80) @Pattern(regexp = TextRules.SINGLE_LINE) String> educationFields,
    @Min(0) @Max(100_000_000) Integer salaryMin,
    @Min(0) @Max(100_000_000) Integer salaryMax,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Use a three-letter ISO currency code") String salaryCurrency,
    SalaryPeriod salaryPeriod,
    @NotBlank(message = "Application URL is required") @Size(max = 500) String applicationUrl,
    @NotBlank(message = "Source is required") @Size(max = 120)
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters") String sourceName,
    @Size(max = 500) String sourceUrl,
    @NotNull(message = "Choose whether this is a real or demo listing") DataOrigin dataOrigin,
    @NotNull(message = "Posting date is required") LocalDate postedOn,
    LocalDate expiresOn,
    @NotNull @Size(min = 1, max = 30, message = "Add 1-30 skills") List<@NotNull @Valid SkillItem> skills) {

  public record SkillItem(@NotNull UUID skillId, @NotNull SkillRequirement requirement) {}
}
