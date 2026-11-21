package com.careermatcher.job;

import com.careermatcher.job.JobEnums.SkillRequirement;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class JobSkillRequirement {

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SkillRequirement requirement;

  protected JobSkillRequirement() {}

  public JobSkillRequirement(UUID skillId, SkillRequirement requirement) {
    this.skillId = skillId;
    this.requirement = requirement;
  }

  public UUID getSkillId() { return skillId; }
  public SkillRequirement getRequirement() { return requirement; }

  @Override
  public boolean equals(Object o) {
    return o instanceof JobSkillRequirement other
        && Objects.equals(skillId, other.skillId)
        && requirement == other.requirement;
  }

  @Override
  public int hashCode() {
    return Objects.hash(skillId, requirement);
  }
}
