package com.careermatcher.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  protected RefreshToken() {}

  RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  boolean isActive(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  void revoke(Instant at, UUID replacement) {
    this.revokedAt = at;
    this.replacedBy = replacement;
  }

  UUID getId() {
    return id;
  }

  UUID getUserId() {
    return userId;
  }

  UUID getFamilyId() {
    return familyId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getRevokedAt() {
    return revokedAt;
  }

  UUID getReplacedBy() {
    return replacedBy;
  }
}
