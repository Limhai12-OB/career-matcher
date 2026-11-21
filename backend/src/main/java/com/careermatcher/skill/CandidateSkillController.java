package com.careermatcher.skill;

import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/skills")
@Tag(name = "Skills", description = "The candidate's confirmed skills")
@SecurityRequirement(name = "bearerAuth")
public class CandidateSkillController {

  private final CandidateSkillService service;

  public CandidateSkillController(CandidateSkillService service) {
    this.service = service;
  }

  @Operation(summary = "List my confirmed skills")
  @GetMapping
  public List<CandidateSkillResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.list(user.id());
  }

  @Operation(summary = "Remove a skill from my profile")
  @DeleteMapping("/{skillId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID skillId) {
    service.remove(user.id(), skillId);
    return ResponseEntity.noContent().build();
  }
}
