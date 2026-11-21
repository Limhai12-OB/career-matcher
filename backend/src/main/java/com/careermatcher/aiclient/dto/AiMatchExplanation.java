package com.careermatcher.aiclient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Explanation returned by the AI service; validated again against the facts before use. */
public record AiMatchExplanation(
    @NotBlank @Size(max = 40) String provider,
    @NotBlank @Size(max = 80) String model,
    @NotBlank @Size(max = 40) String promptVersion,
    @NotBlank @Size(min = 20, max = 600) String summary,
    @NotNull @Size(max = 4) List<@NotNull @Valid Point> strengths,
    @NotNull @Size(max = 4) List<@NotNull @Valid Point> gaps,
    @NotNull @Size(max = 4) List<@NotNull @Valid Point> nextSteps) {

  public record Point(
      @NotBlank @Size(min = 5, max = 240) String text,
      @NotNull @Size(max = 10) List<@NotBlank @Size(max = 80) String> skills) {}
}
