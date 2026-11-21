package com.careermatcher.common.security;

import com.careermatcher.user.UserLookup;
import com.careermatcher.user.UserStatus;
import com.careermatcher.user.UserTokenState;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a cryptographically valid JWT into an authenticated user, additionally checking against
 * the database that the account still exists, is active, and that the token was issued for the
 * current credentials (token version). This makes password changes and account deletion take
 * effect immediately rather than when the access token expires.
 */
@Component
public class AccessTokenAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String CLAIM_TOKEN_VERSION = "ver";

  private final UserLookup userLookup;

  public AccessTokenAuthenticationConverter(UserLookup userLookup) {
    this.userLookup = userLookup;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new InvalidBearerTokenException("Invalid subject");
    }
    Object ver = jwt.getClaims().get(CLAIM_TOKEN_VERSION);
    if (!(ver instanceof Number tokenVersion)) {
      throw new InvalidBearerTokenException("Missing token version");
    }
    UserTokenState state =
        userLookup
            .tokenState(userId)
            .filter(s -> s.status() == UserStatus.ACTIVE)
            .filter(s -> s.tokenVersion() == tokenVersion.intValue())
            .orElseThrow(() -> new InvalidBearerTokenException("Token no longer valid"));
    // Role is taken from the database, not the token, so role changes apply immediately.
    return new UserAuthenticationToken(new AuthenticatedUser(state.id(), state.role()));
  }
}
