package com.careermatcher.job;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EducationLevel;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.SalaryPeriod;
import com.careermatcher.job.JobEnums.Seniority;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {

  /** Editable content of a job, validated by {@link JobRules} before being applied. */
  public record Content(
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
      LocalDate postedOn,
      LocalDate expiresOn,
      Set<JobSkillRequirement> skills) {}

  @Id private UUID id;

  @Column(nullable = false, length = 150) private String title;
  @Column(name = "company_name", nullable = false, length = 120) private String companyName;
  @Column(name = "company_website", length = 500) private String companyWebsite;
  @Column(nullable = false, columnDefinition = "text") private String description;
  @Column(length = 100) private String city;
  @Column(name = "country_code", length = 2) private String countryCode;

  @Enumerated(EnumType.STRING) @Column(name = "remote_type", nullable = false, length = 10)
  private RemoteType remoteType;

  @Enumerated(EnumType.STRING) @Column(name = "employment_type", nullable = false, length = 20)
  private EmploymentType employmentType;

  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private Seniority seniority;

  @Column(name = "min_experience_months", nullable = false) private int minExperienceMonths;
  @Column(name = "max_experience_months") private Integer maxExperienceMonths;

  @Enumerated(EnumType.STRING) @Column(name = "education_level", length = 20)
  private EducationLevel educationLevel;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "education_fields", nullable = false, columnDefinition = "text[]")
  private List<String> educationFields = new ArrayList<>();

  @Column(name = "salary_min") private Integer salaryMin;
  @Column(name = "salary_max") private Integer salaryMax;
  @Column(name = "salary_currency", length = 3) private String salaryCurrency;

  @Enumerated(EnumType.STRING) @Column(name = "salary_period", length = 10)
  private SalaryPeriod salaryPeriod;

  @Column(name = "application_url", nullable = false, length = 500) private String applicationUrl;
  @Column(name = "source_name", nullable = false, length = 120) private String sourceName;
  @Column(name = "source_url", length = 500) private String sourceUrl;

  @Enumerated(EnumType.STRING) @Column(name = "data_origin", nullable = false, length = 10)
  private DataOrigin dataOrigin;

  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
  private JobStatus status;

  @Column(name = "posted_on", nullable = false) private LocalDate postedOn;
  @Column(name = "expires_on") private LocalDate expiresOn;
  @Column(name = "published_at") private Instant publishedAt;
  @Column(name = "created_by") private UUID createdBy;
  @Column(name = "updated_by") private UUID updatedBy;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "job_skills", joinColumns = @JoinColumn(name = "job_id"))
  private Set<JobSkillRequirement> skills = new HashSet<>();

  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected Job() {}

  public Job(Content content, UUID actor) {
    this.id = UUID.randomUUID();
    this.status = JobStatus.DRAFT;
    this.createdBy = actor;
    apply(content, actor);
  }

  public void apply(Content c, UUID actor) {
    title = c.title();
    companyName = c.companyName();
    companyWebsite = c.companyWebsite();
    description = c.description();
    city = c.city();
    countryCode = c.countryCode();
    remoteType = c.remoteType();
    employmentType = c.employmentType();
    seniority = c.seniority();
    minExperienceMonths = c.minExperienceMonths();
    maxExperienceMonths = c.maxExperienceMonths();
    educationLevel = c.educationLevel();
    educationFields = new ArrayList<>(c.educationFields());
    salaryMin = c.salaryMin();
    salaryMax = c.salaryMax();
    salaryCurrency = c.salaryCurrency();
    salaryPeriod = c.salaryPeriod();
    applicationUrl = c.applicationUrl();
    sourceName = c.sourceName();
    sourceUrl = c.sourceUrl();
    dataOrigin = c.dataOrigin();
    postedOn = c.postedOn();
    expiresOn = c.expiresOn();
    skills.clear();
    skills.addAll(c.skills());
    updatedBy = actor;
  }

  public void changeStatus(JobStatus next, Instant now, UUID actor) {
    if (next == JobStatus.PUBLISHED && publishedAt == null) {
      publishedAt = now;
    }
    status = next;
    updatedBy = actor;
  }

  public UUID getId() { return id; }
  public String getTitle() { return title; }
  public String getCompanyName() { return companyName; }
  public String getCompanyWebsite() { return companyWebsite; }
  public String getDescription() { return description; }
  public String getCity() { return city; }
  public String getCountryCode() { return countryCode; }
  public RemoteType getRemoteType() { return remoteType; }
  public EmploymentType getEmploymentType() { return employmentType; }
  public Seniority getSeniority() { return seniority; }
  public int getMinExperienceMonths() { return minExperienceMonths; }
  public Integer getMaxExperienceMonths() { return maxExperienceMonths; }
  public EducationLevel getEducationLevel() { return educationLevel; }
  public List<String> getEducationFields() { return List.copyOf(educationFields); }
  public Integer getSalaryMin() { return salaryMin; }
  public Integer getSalaryMax() { return salaryMax; }
  public String getSalaryCurrency() { return salaryCurrency; }
  public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
  public String getApplicationUrl() { return applicationUrl; }
  public String getSourceName() { return sourceName; }
  public String getSourceUrl() { return sourceUrl; }
  public DataOrigin getDataOrigin() { return dataOrigin; }
  public JobStatus getStatus() { return status; }
  public LocalDate getPostedOn() { return postedOn; }
  public LocalDate getExpiresOn() { return expiresOn; }
  public Instant getPublishedAt() { return publishedAt; }
  public Set<JobSkillRequirement> getSkills() { return Set.copyOf(skills); }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
