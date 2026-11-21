package com.careermatcher.job.dto;

import com.careermatcher.job.JobEnums.SkillRequirement;
import com.careermatcher.skill.SkillCategory;
import java.util.UUID;

public record JobSkillView(UUID skillId, String slug, String name, SkillCategory category, SkillRequirement requirement) {}
