package com.careermatcher.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A stored match result version (rows are immutable; inserted via an idempotent upsert). */
@Entity
@Immutable
@Table(name = "job_matches")
class JobMatch {

  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "job_id", nullable = false) private UUID jobId;
  @Column(name = "algorithm_version", nullable = false, length = 20) private String algorithmVersion;
  @Column(name = "total_score", nullable = false, precision = 4, scale = 1) private BigDecimal totalScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String breakdown;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String weights;

  @Column(name = "cv_analysis_id") private UUID cvAnalysisId;
  @Column(name = "result_hash", nullable = false, length = 64) private String resultHash;
  @Column(name = "computed_at", nullable = false) private Instant computedAt;

  protected JobMatch() {}

  UUID getId() { return id; }
  UUID getJobId() { return jobId; }
  String getAlgorithmVersion() { return algorithmVersion; }
  String getBreakdown() { return breakdown; }
  String getResultHash() { return resultHash; }
  Instant getComputedAt() { return computedAt; }
}
