package com.careermatcher.cv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cv_analyses")
class CvAnalysis {

  @Id private UUID id;

  @Column(name = "document_id", nullable = false, updatable = false)
  private UUID documentId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AnalysisStatus status;

  @Column(name = "error_code", length = 40)
  private String errorCode;

  @Column(length = 40)
  private String provider;

  @Column(length = 80)
  private String model;

  @Column(name = "prompt_version", length = 40)
  private String promptVersion;

  @Column(name = "schema_version", length = 40)
  private String schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String result;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private List<String> warnings = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Version private Long version;

  protected CvAnalysis() {}

  CvAnalysis(UUID documentId, UUID userId, Instant now) {
    this.id = UUID.randomUUID();
    this.documentId = documentId;
    this.userId = userId;
    this.status = AnalysisStatus.PENDING;
    this.createdAt = now;
  }

  void complete(
      String provider, String model, String promptVersion, String schemaVersion, String resultJson,
      List<String> warnings, Instant now) {
    this.status = AnalysisStatus.COMPLETED;
    this.provider = provider;
    this.model = model;
    this.promptVersion = promptVersion;
    this.schemaVersion = schemaVersion;
    this.result = resultJson;
    this.warnings = new ArrayList<>(warnings);
    this.completedAt = now;
    this.errorCode = null;
  }

  void fail(String errorCode, Instant now) {
    this.status = AnalysisStatus.FAILED;
    this.errorCode = errorCode;
    this.completedAt = now;
  }

  void markApplied(Instant now) {
    this.appliedAt = now;
  }

  UUID getId() { return id; }
  UUID getDocumentId() { return documentId; }
  UUID getUserId() { return userId; }
  AnalysisStatus getStatus() { return status; }
  String getErrorCode() { return errorCode; }
  String getProvider() { return provider; }
  String getModel() { return model; }
  String getPromptVersion() { return promptVersion; }
  String getResult() { return result; }
  List<String> getWarnings() { return List.copyOf(warnings); }
  Instant getCreatedAt() { return createdAt; }
  Instant getStartedAt() { return startedAt; }
  Instant getCompletedAt() { return completedAt; }
  Instant getAppliedAt() { return appliedAt; }
}
