package com.careermatcher.auth;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.security.RefreshTokenProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens. Each use returns a new token and revokes the old one. Presenting
 * an already-rotated token is treated as theft and revokes the whole family, except within a short
 * grace window that tolerates benign races between browser tabs.
 */
@Service
class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  record IssuedRefreshToken(String value, UUID userId, Instant expiresAt) {}

  private final RefreshTokenRepository repository;
  private final RefreshTokenProperties props;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties props, Clock clock) {
    this.repository = repository;
    this.props = props;
    this.clock = clock;
  }

  @Transactional
  IssuedRefreshToken issueNewFamily(UUID userId) {
    return issue(userId, UUID.randomUUID()).issued();
  }

  /** Validates and rotates the presented token. */
  @Transactional(noRollbackFor = ApiException.class)
  IssuedRefreshToken rotate(String presented) {
    Instant now = clock.instant();
    RefreshToken current =
        repository.findByHashForUpdate(hash(presented)).orElseThrow(RefreshTokenService::invalid);

    if (current.getRevokedAt() != null) {
      boolean recentlyRotated =
          current.getReplacedBy() != null
              && current.getRevokedAt().plus(props.reuseGrace()).isAfter(now);
      if (recentlyRotated) {
        // Likely a concurrent refresh from another tab; the browser already holds the new cookie.
        throw new ApiException(
            HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_ROTATED", "Session was refreshed elsewhere.");
      }
      int revoked = repository.revokeFamily(current.getFamilyId(), now);
      log.warn("Refresh token reuse detected; revoked {} token(s) in family", revoked);
      throw invalid();
    }
    if (!current.getExpiresAt().isAfter(now)) {
      throw invalid();
    }
    Created next = issue(current.getUserId(), current.getFamilyId());
    current.revoke(now, next.entity().getId());
    return next.issued();
  }

  /** Revokes the session the presented token belongs to. Unknown tokens are ignored. */
  @Transactional
  void revokeFamilyOf(String presented) {
    repository
        .findByHashForUpdate(hash(presented))
        .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
  }

  @Transactional
  void revokeAllForUser(UUID userId) {
    repository.revokeAllForUser(userId, clock.instant());
  }

  /** Daily cleanup of tokens that expired more than a day ago. */
  @Scheduled(cron = "${app.security.refresh.cleanup-cron:0 17 3 * * *}")
  @Transactional
  public void purgeExpired() {
    int deleted = repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    if (deleted > 0) {
      log.info("Purged {} expired refresh tokens", deleted);
    }
  }

  private record Created(RefreshToken entity, IssuedRefreshToken issued) {}

  private Created issue(UUID userId, UUID familyId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(props.ttl());
    RefreshToken entity = repository.save(new RefreshToken(userId, familyId, hash(value), now, expiresAt));
    return new Created(entity, new IssuedRefreshToken(value, userId, expiresAt));
  }

  static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ApiException invalid() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
  }
}
