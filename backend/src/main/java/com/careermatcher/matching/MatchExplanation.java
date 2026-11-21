package com.careermatcher.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "match_explanations")
class MatchExplanation {

  @Id private UUID id;
  @Column(name = "match_id", nullable = false) private UUID matchId;
  @Column(nullable = false, length = 40) private String provider;
  @Column(nullable = false, length = 80) private String model;
  @Column(name = "prompt_version", nullable = false, length = 40) private String promptVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String content;

  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected MatchExplanation() {}

  String getProvider() { return provider; }
  String getModel() { return model; }
  String getContent() { return content; }
  Instant getCreatedAt() { return createdAt; }
}
