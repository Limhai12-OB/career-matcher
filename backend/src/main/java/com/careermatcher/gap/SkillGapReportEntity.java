package com.careermatcher.gap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "skill_gap_reports")
class SkillGapReportEntity {

  @Id private UUID id;
  @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
  @Column(name = "algorithm_version", nullable = false, length = 20) private String algorithmVersion;
  @Column(name = "match_algorithm_version", nullable = false, length = 20) private String matchAlgorithmVersion;
  @Column(name = "target_job_count", nullable = false) private int targetJobCount;
  @Column(name = "average_score", nullable = false, precision = 4, scale = 1) private BigDecimal averageScore;
  @Column(name = "required_coverage", nullable = false, precision = 4, scale = 1) private BigDecimal requiredCoverage;
  @Column(name = "critical_count", nullable = false) private int criticalCount;
  @Column(name = "high_count", nullable = false) private int highCount;

  @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
  private String report;

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
  private String roadmap;

  @Column(name = "roadmap_provider", length = 40) private String roadmapProvider;
  @Column(name = "roadmap_model", length = 80) private String roadmapModel;
  @Column(name = "roadmap_prompt_version", length = 40) private String roadmapPromptVersion;
  @Column(name = "roadmap_created_at") private Instant roadmapCreatedAt;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Version private Long version;

  protected SkillGapReportEntity() {}

  SkillGapReportEntity(UUID userId, String algorithmVersion, String matchVersion, int targets, double avg,
      double coverage, int critical, int high, String reportJson, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.algorithmVersion = algorithmVersion;
    this.matchAlgorithmVersion = matchVersion;
    this.targetJobCount = targets;
    this.averageScore = BigDecimal.valueOf(avg);
    this.requiredCoverage = BigDecimal.valueOf(coverage);
    this.criticalCount = critical;
    this.highCount = high;
    this.report = reportJson;
    this.createdAt = now;
  }

  void attachRoadmap(String json, String provider, String model, String promptVersion, Instant now) {
    this.roadmap = json;
    this.roadmapProvider = provider;
    this.roadmapModel = model;
    this.roadmapPromptVersion = promptVersion;
    this.roadmapCreatedAt = now;
  }

  UUID getId() { return id; }
  String getAlgorithmVersion() { return algorithmVersion; }
  String getMatchAlgorithmVersion() { return matchAlgorithmVersion; }
  int getTargetJobCount() { return targetJobCount; }
  double getAverageScore() { return averageScore.doubleValue(); }
  double getRequiredCoverage() { return requiredCoverage.doubleValue(); }
  int getCriticalCount() { return criticalCount; }
  int getHighCount() { return highCount; }
  String getReport() { return report; }
  String getRoadmap() { return roadmap; }
  String getRoadmapProvider() { return roadmapProvider; }
  String getRoadmapModel() { return roadmapModel; }
  Instant getRoadmapCreatedAt() { return roadmapCreatedAt; }
  Instant getCreatedAt() { return createdAt; }
}
