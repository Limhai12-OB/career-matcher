package com.careermatcher.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_skills")
class CandidateSkill {

  enum Source { CV, MANUAL }

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "skill_id", nullable = false, updatable = false)
  private UUID skillId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Source source;

  @Column(name = "analysis_id")
  private UUID analysisId;

  @Column(length = 300)
  private String evidence;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CandidateSkill() {}

  CandidateSkill(UUID userId, UUID skillId, Source source, UUID analysisId, String evidence, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.skillId = skillId;
    this.source = source;
    this.analysisId = analysisId;
    this.evidence = evidence;
    this.createdAt = now;
  }

  void refreshFromCv(UUID analysisId, String evidence) {
    if (source == Source.CV) {
      this.analysisId = analysisId;
      this.evidence = evidence;
    }
  }

  UUID getSkillId() { return skillId; }
  Source getSource() { return source; }
  UUID getAnalysisId() { return analysisId; }
  String getEvidence() { return evidence; }
  Instant getCreatedAt() { return createdAt; }
}
