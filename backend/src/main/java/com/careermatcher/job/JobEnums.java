package com.careermatcher.job;

/** Enumerations of the job module (one file for compactness; each is public API). */
public final class JobEnums {
  private JobEnums() {}

  public enum RemoteType { ONSITE, HYBRID, REMOTE }

  public enum EmploymentType { FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT }

  public enum Seniority { INTERNSHIP, ENTRY, JUNIOR }

  public enum EducationLevel { HIGH_SCHOOL, VOCATIONAL, ASSOCIATE, BACHELOR, MASTER }

  public enum SalaryPeriod { MONTH, YEAR }

  /** REAL: a genuine opening entered by an admin. DEMO: demonstration data, never a real job. */
  public enum DataOrigin { REAL, DEMO }

  public enum JobStatus { DRAFT, PUBLISHED, CLOSED }

  public enum SkillRequirement { REQUIRED, PREFERRED }
}
