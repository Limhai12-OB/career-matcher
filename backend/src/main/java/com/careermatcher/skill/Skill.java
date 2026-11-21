package com.careermatcher.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Canonical catalog skill (seeded by migration, read-only to the application). */
@Entity
@Immutable
@Table(name = "skills")
class Skill {

  @Id private UUID id;

  @Column(nullable = false, length = 80)
  private String slug;

  @Column(nullable = false, length = 80)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SkillCategory category;

  protected Skill() {}

  UUID getId() { return id; }
  String getSlug() { return slug; }
  String getName() { return name; }
  SkillCategory getCategory() { return category; }

  SkillRef toRef() {
    return new SkillRef(id, slug, name, category);
  }
}
