package com.careermatcher.job;

import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.EmploymentType;
import com.careermatcher.job.JobEnums.RemoteType;
import com.careermatcher.job.JobEnums.Seniority;
import com.careermatcher.job.dto.JobDetail;
import com.careermatcher.job.dto.JobSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Validated
@Tag(name = "Jobs", description = "Search published job listings")
@SecurityRequirement(name = "bearerAuth")
public class JobController {

  private final JobService service;

  public JobController(JobService service) {
    this.service = service;
  }

  @Operation(
      summary = "Search published jobs",
      description = "Newest first. `origin` filters REAL vs DEMO listings; omit it to include both.")
  @GetMapping
  public Page<JobSummary> search(
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country,
      @RequestParam(required = false) RemoteType remote,
      @RequestParam(required = false) EmploymentType type,
      @RequestParam(required = false) Seniority seniority,
      @RequestParam(required = false) @Size(max = 10) List<@Pattern(regexp = "^[a-z0-9-]{1,80}$") String> skill,
      @RequestParam(required = false) DataOrigin origin,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
    return service.search(
        new JobService.Criteria(q, country, remote, type, seniority, skill, origin, null), page, size);
  }

  @Operation(summary = "Get a published job")
  @GetMapping("/{id}")
  public JobDetail get(@PathVariable UUID id) {
    return service.getPublished(id);
  }
}
