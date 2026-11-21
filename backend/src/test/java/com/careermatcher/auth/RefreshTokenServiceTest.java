package com.careermatcher.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.security.RefreshTokenProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

  private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
  private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
  private final RefreshTokenProperties props =
      new RefreshTokenProperties(
          Duration.ofDays(14), "cm_refresh", "/api/v1/auth", true, "Strict", Duration.ofSeconds(10));

  private RefreshTokenService serviceAt(Instant now) {
    return new RefreshTokenService(repo, props, Clock.fixed(now, ZoneOffset.UTC));
  }

  private RefreshToken rotatedTokenAt(Instant revokedAt) {
    RefreshToken t =
        new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "h", T0, T0.plus(Duration.ofDays(1)));
    t.revoke(revokedAt, UUID.randomUUID());
    return t;
  }

  @Test
  void reuseWithinGraceWindowIsTreatedAsBenignRace() {
    RefreshToken token = rotatedTokenAt(T0);
    when(repo.findByHashForUpdate(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> serviceAt(T0.plusSeconds(3)).rotate("presented"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo("REFRESH_TOKEN_ROTATED");
    verify(repo, never()).revokeFamily(any(), any());
  }

  @Test
  void reuseAfterGraceWindowRevokesFamily() {
    RefreshToken token = rotatedTokenAt(T0);
    when(repo.findByHashForUpdate(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> serviceAt(T0.plusSeconds(30)).rotate("presented"))
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo("INVALID_REFRESH_TOKEN");
    verify(repo).revokeFamily(eq(token.getFamilyId()), any());
  }

  @Test
  void expiredTokenIsRejected() {
    RefreshToken token =
        new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "h", T0, T0.plusSeconds(60));
    when(repo.findByHashForUpdate(any())).thenReturn(Optional.of(token));
    assertThatThrownBy(() -> serviceAt(T0.plusSeconds(61)).rotate("presented"))
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo("INVALID_REFRESH_TOKEN");
  }

  @Test
  void hashIsDeterministicSha256Hex() {
    assertThat(RefreshTokenService.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
