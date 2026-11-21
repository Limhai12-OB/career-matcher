package com.careermatcher.cv.dto;

import com.careermatcher.cv.AnalysisStatus;
import java.time.Instant;
import java.util.UUID;

public record AnalysisSummary(
    UUID id,
    UUID documentId,
    AnalysisStatus status,
    String errorCode,
    Instant createdAt,
    Instant completedAt,
    Instant appliedAt) {}
