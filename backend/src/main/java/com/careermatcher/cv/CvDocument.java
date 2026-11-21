package com.careermatcher.cv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_documents")
class CvDocument {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
  private String storageKey;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 10)
  private DocumentType documentType;

  @Column(name = "size_bytes", nullable = false)
  private int sizeBytes;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CvDocument() {}

  CvDocument(UUID userId, String filename, DocumentType type, int size, String sha256, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    // Random key: no user-controlled data in object names.
    this.storageKey = "cv/" + userId + "/" + UUID.randomUUID();
    this.originalFilename = filename;
    this.documentType = type;
    this.sizeBytes = size;
    this.sha256 = sha256;
    this.createdAt = now;
  }

  UUID getId() { return id; }
  UUID getUserId() { return userId; }
  String getStorageKey() { return storageKey; }
  String getOriginalFilename() { return originalFilename; }
  DocumentType getDocumentType() { return documentType; }
  int getSizeBytes() { return sizeBytes; }
  String getSha256() { return sha256; }
  Instant getCreatedAt() { return createdAt; }
}
