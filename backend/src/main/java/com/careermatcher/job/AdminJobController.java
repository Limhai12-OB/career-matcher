package com.careermatcher.job;

import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobEnums.JobStatus;
import com.careermatcher.job.dto.JobDetail;
import com.careermatcher.job.dto.JobRequest;
import com.careermatcher.job.dto.JobStatusRequest;
import com.careermatcher.job.dto.JobSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced by SecurityConfig for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@Validated
@Tag(name = "Admin: jobs", description = "Manage job listings (ADMIN role)")
@SecurityRequirement(name = "bearerAuth")
public class AdminJobController {

  private final JobService service;

  public AdminJobController(JobService service) {
    this.service = service;
  }

  @Operation(summary = "List all jobs (any status)")
  @GetMapping
  public Page<JobSummary> list(
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) JobStatus status,
      @RequestParam(required = false) DataOrigin origin,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.adminSearch(
        new JobService.Criteria(q, null, null, null, null, null, origin, status), page, size);
  }

  @Operation(summary = "Get any job")
  @GetMapping("/{id}")
  public JobDetail get(@PathVariable UUID id) {
    return service.adminGet(id);
  }

  @Operation(summary = "Create a job (starts as DRAFT)")
  @PostMapping
  public ResponseEntity<JobDetail> create(
      @AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody JobRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, admin.id()));
  }

  @Operation(summary = "Replace a job")
  @PutMapping("/{id}")
  public JobDetail update(
      @AuthenticationPrincipal AuthenticatedUser admin,
      @PathVariable UUID id,
      @Valid @RequestBody JobRequest request) {
    return service.update(id, request, admin.id());
  }

  @Operation(summary = "Publish, close or (if never published) return to draft")
  @PatchMapping("/{id}/status")
  public JobDetail changeStatus(
      @AuthenticationPrincipal AuthenticatedUser admin,
      @PathVariable UUID id,
      @Valid @RequestBody JobStatusRequest request) {
    return service.changeStatus(id, request.status(), admin.id());
  }

  @Operation(summary = "Delete a job permanently")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
    service.delete(id, admin.id());
    return ResponseEntity.noContent().build();
  }
}
