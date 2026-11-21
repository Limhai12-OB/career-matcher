package com.careermatcher.skill;

import java.util.UUID;

/** Public, immutable view of a catalog skill for other modules. */
public record SkillRef(UUID id, String slug, String name, SkillCategory category) {}
