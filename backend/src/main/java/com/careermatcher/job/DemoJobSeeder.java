package com.careermatcher.job;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EducationLevel;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.SalaryPeriod;
import com.careermatcher.job.JobEnums.Seniority;
import com.careermatcher.job.JobEnums.SkillRequirement;
import com.careermatcher.job.dto.JobRequest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the bundled demonstration job dataset when {@code app.jobs.demo-data-enabled=true}. Every
 * listing is stored with origin DEMO and links only to reserved example.com domains. Idempotent:
 * does nothing if the dataset (identified by its source name) was already loaded. Disabled by default; never enable it to "fill up" a
 * production catalogue.
 */
@Component
public class DemoJobSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoJobSeeder.class);
  static final String RESOURCE = "demo/demo-jobs.json";

  record Dataset(String notice, String sourceName, String sourceUrl, List<DemoJob> jobs) {}

  record DemoJob(
      String slug, String title, String companyName, String city, String countryCode, RemoteType remoteType,
      EmploymentType employmentType, Seniority seniority, int minExperienceMonths, Integer maxExperienceMonths,
      EducationLevel educationLevel, List<String> educationFields, Integer salaryMin, Integer salaryMax,
      String salaryCurrency, SalaryPeriod salaryPeriod, List<String> requiredSkills, List<String> preferredSkills,
      int postedDaysAgo, String description) {}

  private final boolean enabled;
  private final JobRepository jobs;
  private final JobRules rules;
  private final com.careermatcher.skill.SkillNormalizer catalog;
  private final JsonMapper json;
  private final Clock clock;

  public DemoJobSeeder(
      @Value("${app.jobs.demo-data-enabled:false}") boolean enabled,
      JobRepository jobs,
      JobRules rules,
      com.careermatcher.skill.SkillNormalizer catalog,
      JsonMapper json,
      Clock clock) {
    this.enabled = enabled;
    this.jobs = jobs;
    this.rules = rules;
    this.catalog = catalog;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (enabled) {
      seed();
    }
  }

  /** Inserts the dataset unless it was loaded before. Returns the number inserted. */
  @Transactional
  public int seed() {
    Dataset data = load();
    if (jobs.existsByDataOriginAndSourceName(DataOrigin.DEMO, data.sourceName())) {
      return 0;
    }
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    int count = 0;
    for (DemoJob d : data.jobs()) {
      List<JobRequest.SkillItem> skills = new ArrayList<>();
      d.requiredSkills().forEach(s -> skills.add(item(s, SkillRequirement.REQUIRED, d.slug())));
      d.preferredSkills().forEach(s -> skills.add(item(s, SkillRequirement.PREFERRED, d.slug())));
      JobRequest request =
          new JobRequest(
              d.title(), d.companyName(), "https://example.com/demo-companies/" + d.slug(), d.description(),
              d.city() == null || d.city().isBlank() ? null : d.city(), d.countryCode(), d.remoteType(),
              d.employmentType(), d.seniority(), d.minExperienceMonths(), d.maxExperienceMonths(),
              d.educationLevel(), d.educationFields(), d.salaryMin(), d.salaryMax(), d.salaryCurrency(),
              d.salaryPeriod(), "https://careers.example.com/demo/" + d.slug(), data.sourceName(),
              data.sourceUrl(), DataOrigin.DEMO, today.minusDays(d.postedDaysAgo()), null, skills);
      Job job = new Job(rules.toContent(request), null);
      job.changeStatus(JobStatus.PUBLISHED, clock.instant(), null);
      jobs.save(job);
      count++;
    }
    log.info("Seeded {} DEMO job listings (demonstration data, not real openings)", count);
    return count;
  }

  private JobRequest.SkillItem item(String slug, SkillRequirement requirement, String job) {
    UUID id =
        catalog
            .bySlug(slug)
            .orElseThrow(() -> new IllegalStateException("Demo job " + job + " references unknown skill " + slug))
            .id();
    return new JobRequest.SkillItem(id, requirement);
  }

  private Dataset load() {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      return json.readValue(in, Dataset.class);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + RESOURCE, e);
    }
  }
}
