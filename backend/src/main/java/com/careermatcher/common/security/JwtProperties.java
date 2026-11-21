package com.careermatcher.common.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Access-token signing settings. The secret must be at least 256 bits for HS256. */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
    @NotBlank String secret,
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotNull Duration accessTokenTtl) {

  @AssertTrue(message = "app.security.jwt.secret (JWT_SECRET) must be at least 32 bytes")
  public boolean isSecretLongEnough() {
    return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
  }

  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", secret=***]";
  }
}
