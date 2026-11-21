package com.careermatcher.common.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class UserAuthenticationToken extends AbstractAuthenticationToken {

  private final AuthenticatedUser principal;

  public UserAuthenticationToken(AuthenticatedUser principal) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null; // the raw token is not retained
  }

  @Override
  public AuthenticatedUser getPrincipal() {
    return principal;
  }
}
