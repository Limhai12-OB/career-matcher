package com.careermatcher.skill;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
@Validated
@Tag(name = "Skill catalog")
@SecurityRequirement(name = "bearerAuth")
public class SkillCatalogController {

  private final SkillNormalizer catalog;

  public SkillCatalogController(SkillNormalizer catalog) {
    this.catalog = catalog;
  }

  @Operation(summary = "Search the canonical skill catalog (name or alias); all skills when q is empty")
  @GetMapping
  public List<SkillRef> search(@RequestParam(required = false) @Size(max = 80) String q) {
    return catalog.search(q);
  }
}
