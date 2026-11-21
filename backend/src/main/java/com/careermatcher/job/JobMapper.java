package com.careermatcher.job;

import com.careermatcher.job.JobEnums.SkillRequirement;
import com.careermatcher.job.dto.JobDetail;
import com.careermatcher.job.dto.JobSkillView;
import com.careermatcher.job.dto.JobSummary;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.SkillRef;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class JobMapper {

  private final SkillNormalizer catalog;

  JobMapper(SkillNormalizer catalog) {
    this.catalog = catalog;
  }

  List<JobSkillView> skills(Job job) {
    return job.getSkills().stream()
        .map(
            s -> {
              SkillRef ref = catalog.byId(s.getSkillId()).orElseThrow();
              return new JobSkillView(ref.id(), ref.slug(), ref.name(), ref.category(), s.getRequirement());
            })
        .sorted(
            Comparator.comparing(JobSkillView::requirement)
                .thenComparing(JobSkillView::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  JobSummary summary(Job j) {
    List<JobSkillView> skills = skills(j);
    return new JobSummary(
        j.getId(), j.getTitle(), j.getCompanyName(), j.getCity(), j.getCountryCode(), j.getRemoteType(),
        j.getEmploymentType(), j.getSeniority(), j.getSalaryMin(), j.getSalaryMax(), j.getSalaryCurrency(),
        j.getSalaryPeriod(), j.getPostedOn(), j.getExpiresOn(), j.getDataOrigin(), j.getStatus(),
        skills.stream().filter(s -> s.requirement() == SkillRequirement.REQUIRED).map(JobSkillView::name).toList(),
        (int) skills.stream().filter(s -> s.requirement() == SkillRequirement.PREFERRED).count());
  }

  JobDetail detail(Job j) {
    return new JobDetail(
        j.getId(), j.getTitle(), j.getCompanyName(), j.getCompanyWebsite(), j.getDescription(), j.getCity(),
        j.getCountryCode(), j.getRemoteType(), j.getEmploymentType(), j.getSeniority(),
        j.getMinExperienceMonths(), j.getMaxExperienceMonths(), j.getEducationLevel(), j.getEducationFields(),
        j.getSalaryMin(), j.getSalaryMax(), j.getSalaryCurrency(), j.getSalaryPeriod(), j.getApplicationUrl(),
        j.getSourceName(), j.getSourceUrl(), j.getDataOrigin(), j.getStatus(), j.getPostedOn(),
        j.getExpiresOn(), j.getPublishedAt(), j.getUpdatedAt(), skills(j));
  }
}
