package com.careermatcher.matching;

import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.matching.dto.MatchDtos.Explanation;
import com.careermatcher.matching.dto.MatchDtos.MatchDetail;
import com.careermatcher.matching.dto.MatchDtos.Recommendations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated candidate's matches (CANDIDATE role; see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/me/matches")
@Validated
@Tag(name = "Matching", description = "Deterministic job compatibility scores and explanations")
@SecurityRequirement(name = "bearerAuth")
public class MatchController {

  private final MatchService service;

  public MatchController(MatchService service) {
    this.service = service;
  }

  @Operation(summary = "Personalized job recommendations, best match first")
  @GetMapping
  public Recommendations recommendations(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) DataOrigin origin,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return service.recommendations(user.id(), origin, page, size);
  }

  @Operation(summary = "Compatibility breakdown for one published job")
  @GetMapping("/{jobId}")
  public MatchDetail detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID jobId) {
    return service.detail(user.id(), jobId);
  }

  @Operation(
      summary = "Explain a match in plain language",
      description = "Generated only from the calculated breakdown; cached per result version.")
  @PostMapping("/{jobId}/explanation")
  public Explanation explain(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID jobId) {
    return service.explain(user.id(), jobId);
  }
}
