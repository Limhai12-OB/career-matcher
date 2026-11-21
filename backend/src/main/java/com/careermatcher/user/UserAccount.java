package com.careermatcher.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class UserAccount {

  @Id private UUID id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "token_version", nullable = false)
  private int tokenVersion;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected UserAccount() {}

  public UserAccount(String email, String passwordHash, Role role) {
    this.id = UUID.randomUUID();
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = UserStatus.ACTIVE;
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /** Changes the password and invalidates every previously issued access token. */
  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.tokenVersion++;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
