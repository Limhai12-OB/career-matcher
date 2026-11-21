package com.careermatcher.cv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cv_analysis_skills")
class CvAnalysisSkill {

  @Id private UUID id;

  @Column(name = "analysis_id", nullable = false, updatable = false)
  private UUID analysisId;

  @Column(nullable = false)
  private int position;

  @Column(name = "raw_name", nullable = false, length = 80)
  private String rawName;

  /** Null when the name did not match the catalog. */
  @Column(name = "skill_id")
  private UUID skillId;

  @Column(nullable = false, length = 30)
  private String category;

  @Column(nullable = false, length = 300)
  private String evidence;

  protected CvAnalysisSkill() {}

  CvAnalysisSkill(UUID analysisId, int position, String rawName, UUID skillId, String category, String evidence) {
    this.id = UUID.randomUUID();
    this.analysisId = analysisId;
    this.position = position;
    this.rawName = rawName;
    this.skillId = skillId;
    this.category = category;
    this.evidence = evidence;
  }

  UUID getId() { return id; }
  int getPosition() { return position; }
  String getRawName() { return rawName; }
  UUID getSkillId() { return skillId; }
  String getCategory() { return category; }
  String getEvidence() { return evidence; }
}
