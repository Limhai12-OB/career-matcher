package com.careermatcher.cv.dto;

import com.careermatcher.cv.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An uploaded CV with its analysis history (newest first). */
public record CvDocumentResponse(
    UUID id,
    String filename,
    DocumentType type,
    int sizeBytes,
    Instant uploadedAt,
    List<AnalysisSummary> analyses) {}
