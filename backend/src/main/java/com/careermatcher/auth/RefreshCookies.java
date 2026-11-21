package com.careermatcher.auth;

import com.careermatcher.common.security.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds and reads the HttpOnly refresh-token cookie. */
@Component
class RefreshCookies {

  private final RefreshTokenProperties props;
  private final Clock clock;

  RefreshCookies(RefreshTokenProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
  }

  ResponseCookie create(String value, Instant expiresAt) {
    return base(value).maxAge(Duration.between(clock.instant(), expiresAt)).build();
  }

  ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  Optional<String> read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(c -> props.cookieName().equals(c.getName()))
        .map(Cookie::getValue)
        .filter(v -> v != null && !v.isBlank() && v.length() <= 128)
        .findFirst();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(props.cookieName(), value)
        .httpOnly(true)
        .secure(props.cookieSecure())
        .sameSite(props.cookieSameSite())
        .path(props.cookiePath());
  }
}
