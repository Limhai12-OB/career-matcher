package com.careermatcher.job.dto;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EducationLevel;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.SalaryPeriod;
import com.careermatcher.job.JobEnums.Seniority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JobDetail(
    UUID id,
    String title,
    String companyName,
    String companyWebsite,
    String description,
    String city,
    String countryCode,
    RemoteType remoteType,
    EmploymentType employmentType,
    Seniority seniority,
    int minExperienceMonths,
    Integer maxExperienceMonths,
    EducationLevel educationLevel,
    List<String> educationFields,
    Integer salaryMin,
    Integer salaryMax,
    String salaryCurrency,
    SalaryPeriod salaryPeriod,
    String applicationUrl,
    String sourceName,
    String sourceUrl,
    DataOrigin dataOrigin,
    JobStatus status,
    LocalDate postedOn,
    LocalDate expiresOn,
    Instant publishedAt,
    Instant updatedAt,
    List<JobSkillView> skills) {}
