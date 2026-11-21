package com.careermatcher.gap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Curated learning resource (seeded by migration; verified URLs). */
@Entity
@Immutable
@Table(name = "skill_resources")
class SkillResource {

  @Id private UUID id;
  @Column(name = "skill_id", nullable = false) private UUID skillId;
  @Column(nullable = false, length = 200) private String title;
  @Column(nullable = false, length = 100) private String provider;
  @Column(name = "resource_type", nullable = false, length = 20) private String resourceType;
  @Column(nullable = false, length = 500) private String url;
  @Column(name = "verified_on", nullable = false) private LocalDate verifiedOn;

  protected SkillResource() {}

  UUID getId() { return id; }
  UUID getSkillId() { return skillId; }
  String getTitle() { return title; }
  String getProvider() { return provider; }
  String getResourceType() { return resourceType; }
  String getUrl() { return url; }
  LocalDate getVerifiedOn() { return verifiedOn; }
}
