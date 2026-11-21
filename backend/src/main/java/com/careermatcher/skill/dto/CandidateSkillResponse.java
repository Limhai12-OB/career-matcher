package com.careermatcher.skill.dto;

import com.careermatcher.skill.SkillCategory;
import java.time.Instant;
import java.util.UUID;

public record CandidateSkillResponse(
    UUID skillId,
    String name,
    SkillCategory category,
    String source,
    UUID analysisId,
    String evidence,
    Instant addedAt) {}
