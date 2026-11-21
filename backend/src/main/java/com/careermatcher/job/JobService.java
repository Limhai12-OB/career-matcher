package com.careermatcher.job;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.Seniority;
import com.careermatcher.job.dto.JobDetail;
import com.careermatcher.job.dto.JobRequest;
import com.careermatcher.job.dto.JobSummary;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.SkillRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job search for candidates and job management for admins. */
@Service
public class JobService {

  private static final Logger log = LoggerFactory.getLogger(JobService.class);
  private static final Sort NEWEST = Sort.by(Sort.Order.desc("postedOn"), Sort.Order.desc("createdAt"));

  /** Search criteria; null fields are not filtered. */
  public record Criteria(
      String query,
      String countryCode,
      RemoteType remoteType,
      EmploymentType employmentType,
      Seniority seniority,
      List<String> skillSlugs,
      DataOrigin dataOrigin,
      JobStatus status) {}

  private final JobRepository jobs;
  private final JobRules rules;
  private final JobMapper mapper;
  private final SkillNormalizer catalog;
  private final Clock clock;

  public JobService(JobRepository jobs, JobRules rules, JobMapper mapper, SkillNormalizer catalog, Clock clock) {
    this.jobs = jobs;
    this.rules = rules;
    this.mapper = mapper;
    this.catalog = catalog;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- candidate-facing

  /** Published, unexpired jobs only. */
  @Transactional(readOnly = true)
  public Page<JobSummary> search(Criteria c, int page, int size) {
    return find(JobSpecifications.all(JobSpecifications.visibleOn(today()), filters(c)), page, size);
  }

  @Transactional(readOnly = true)
  public JobDetail getPublished(UUID id) {
    Job job =
        jobs.findOne(JobSpecifications.all(JobSpecifications.equal("id", id), JobSpecifications.visibleOn(today())))
            .orElseThrow(JobService::notFound);
    return mapper.detail(job);
  }

  /** All published, unexpired jobs (skills initialized), for matching. */
  @Transactional(readOnly = true)
  public List<Job> visibleJobs() {
    List<Job> all = jobs.findAll(JobSpecifications.visibleOn(today()), NEWEST);
    all.forEach(j -> j.getSkills().size());
    return all;
  }

  /** A published, unexpired job (skills initialized), for matching. */
  @Transactional(readOnly = true)
  public Job visibleJob(UUID id) {
    Job job =
        jobs.findOne(JobSpecifications.all(JobSpecifications.equal("id", id), JobSpecifications.visibleOn(today())))
            .orElseThrow(JobService::notFound);
    job.getSkills().size();
    return job;
  }

  public JobSummary summarize(Job job) {
    return mapper.summary(job);
  }

  // ---------------------------------------------------------------- admin

  @Transactional(readOnly = true)
  public Page<JobSummary> adminSearch(Criteria c, int page, int size) {
    return find(JobSpecifications.all(filters(c), JobSpecifications.status(c.status())), page, size);
  }

  @Transactional(readOnly = true)
  public JobDetail adminGet(UUID id) {
    return mapper.detail(load(id));
  }

  @Transactional
  public JobDetail create(JobRequest request, UUID adminId) {
    Job job = jobs.save(new Job(rules.toContent(request), adminId));
    log.info("Job {} created ({}) by admin {}", job.getId(), job.getDataOrigin(), adminId);
    return mapper.detail(job);
  }

  @Transactional
  public JobDetail update(UUID id, JobRequest request, UUID adminId) {
    Job job = load(id);
    job.apply(rules.toContent(request), adminId);
    jobs.saveAndFlush(job);
    return mapper.detail(job);
  }

  @Transactional
  public JobDetail changeStatus(UUID id, JobStatus status, UUID adminId) {
    Job job = load(id);
    if (status == JobStatus.DRAFT && job.getStatus() != JobStatus.DRAFT && job.getPublishedAt() != null) {
      throw new ApiException(
          HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "A published job can be closed, not moved back to draft.");
    }
    job.changeStatus(status, clock.instant(), adminId);
    jobs.saveAndFlush(job);
    log.info("Job {} status -> {} by admin {}", id, status, adminId);
    return mapper.detail(job);
  }

  @Transactional
  public void delete(UUID id, UUID adminId) {
    jobs.delete(load(id));
    log.info("Job {} deleted by admin {}", id, adminId);
  }

  // ---------------------------------------------------------------- helpers

  private Page<JobSummary> find(Specification<Job> spec, int page, int size) {
    return jobs.findAll(spec, PageRequest.of(page, size, NEWEST)).map(mapper::summary);
  }

  private Specification<Job> filters(Criteria c) {
    List<UUID> skillIds = null;
    if (c.skillSlugs() != null && !c.skillSlugs().isEmpty()) {
      skillIds = c.skillSlugs().stream().map(catalog::bySlug).flatMap(Optional::stream).map(SkillRef::id).toList();
      if (skillIds.isEmpty()) {
        // Unknown skills: nothing can match (rather than silently ignoring the filter).
        return (root, q, cb) -> cb.disjunction();
      }
    }
    return JobSpecifications.all(
        JobSpecifications.text(c.query()),
        JobSpecifications.country(c.countryCode()),
        JobSpecifications.remote(c.remoteType()),
        JobSpecifications.employment(c.employmentType()),
        JobSpecifications.seniority(c.seniority()),
        JobSpecifications.origin(c.dataOrigin()),
        JobSpecifications.anySkill(skillIds));
  }

  private Job load(UUID id) {
    return jobs.findById(id).orElseThrow(JobService::notFound);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC));
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
  }
}
