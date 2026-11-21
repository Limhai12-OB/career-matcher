package com.careermatcher.common.security;

import com.careermatcher.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues short-lived access tokens. Contains no personal data beyond the user id. */
@Component
public class AccessTokenIssuer {

  public record IssuedAccessToken(String value, long expiresInSeconds) {}

  private final JwtEncoder encoder;
  private final JwtProperties props;
  private final Clock clock;

  public AccessTokenIssuer(JwtEncoder encoder, JwtProperties props, Clock clock) {
    this.encoder = encoder;
    this.props = props;
    this.clock = clock;
  }

  public IssuedAccessToken issue(UUID userId, Role role, int tokenVersion) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .audience(List.of(props.audience()))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.accessTokenTtl()))
            .id(UUID.randomUUID().toString())
            .claim("role", role.name())
            .claim(AccessTokenAuthenticationConverter.CLAIM_TOKEN_VERSION, tokenVersion)
            .build();
    String value =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new IssuedAccessToken(value, props.accessTokenTtl().toSeconds());
  }
}
