package com.careermatcher.cv.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Catalog skill ids (from the analysis) that the candidate confirms. */
public record ApplySkillsRequest(@NotNull @Size(max = 150) List<@NotNull UUID> skillIds) {}
