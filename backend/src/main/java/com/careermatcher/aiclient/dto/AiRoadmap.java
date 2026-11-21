package com.careermatcher.aiclient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Learning roadmap from the AI service; validated again against the facts before use. */
public record AiRoadmap(
    @NotBlank @Size(max = 40) String provider,
    @NotBlank @Size(max = 80) String model,
    @NotBlank @Size(max = 40) String promptVersion,
    @NotBlank @Size(min = 20, max = 600) String summary,
    @NotNull @Size(min = 1, max = 6) List<@NotNull @Valid Step> steps) {

  public record Step(
      @NotBlank @Size(max = 80) String skill,
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 300) String why,
      @NotNull @Size(min = 1, max = 4) List<@NotBlank @Size(max = 160) String> activities,
      @Min(1) @Max(12) int estimatedWeeks,
      @NotNull @Size(max = 3) List<@NotBlank @Size(max = 40) String> resourceIds) {}
}
