package com.careermatcher.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "skill_aliases")
class SkillAlias {

  @Id
  @Column(length = 120)
  private String alias;

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  protected SkillAlias() {}

  String getAlias() { return alias; }
  UUID getSkillId() { return skillId; }
}
