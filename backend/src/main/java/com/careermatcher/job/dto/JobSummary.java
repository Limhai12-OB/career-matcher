package com.careermatcher.job.dto;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.SalaryPeriod;
import com.careermatcher.job.JobEnums.Seniority;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** List item. {@code status} is only meaningful in admin listings. */
public record JobSummary(
    UUID id,
    String title,
    String companyName,
    String city,
    String countryCode,
    RemoteType remoteType,
    EmploymentType employmentType,
    Seniority seniority,
    Integer salaryMin,
    Integer salaryMax,
    String salaryCurrency,
    SalaryPeriod salaryPeriod,
    LocalDate postedOn,
    LocalDate expiresOn,
    DataOrigin dataOrigin,
    JobStatus status,
    List<String> requiredSkills,
    int preferredSkillCount) {}
