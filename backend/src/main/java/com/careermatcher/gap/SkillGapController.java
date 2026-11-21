package com.careermatcher.gap;

import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.gap.dto.GapDtos.Progress;
import com.careermatcher.gap.dto.GapDtos.ReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated candidate's skill gap reports (CANDIDATE role; see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/me/skill-gaps")
@Tag(name = "Skill gaps", description = "Skill gap analysis, learning roadmap and progress")
@SecurityRequirement(name = "bearerAuth")
public class SkillGapController {

  private final SkillGapService service;

  public SkillGapController(SkillGapService service) {
    this.service = service;
  }

  @Operation(summary = "Analyze my top matches and store a new skill gap report")
  @PostMapping
  public ResponseEntity<ReportView> generate(@AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(user.id()));
  }

  @Operation(summary = "Progress across my reports")
  @GetMapping
  public Progress progress(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.progress(user.id());
  }

  @Operation(summary = "My latest report")
  @GetMapping("/latest")
  public ReportView latest(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.latest(user.id());
  }

  @Operation(summary = "One of my reports")
  @GetMapping("/{reportId}")
  public ReportView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reportId) {
    return service.get(user.id(), reportId);
  }

  @Operation(
      summary = "Generate (once) and return the learning roadmap for a report",
      description = "Grounded in the report's gaps; uses only curated resources; validated before storage.")
  @PostMapping("/{reportId}/roadmap")
  public ReportView roadmap(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reportId) {
    return service.roadmap(user.id(), reportId);
  }
}
