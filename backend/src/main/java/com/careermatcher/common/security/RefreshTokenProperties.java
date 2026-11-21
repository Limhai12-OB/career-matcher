package com.careermatcher.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Refresh-token cookie settings. {@code cookieSecure} must be true in any non-localhost
 * deployment (HTTPS only).
 */
@Validated
@ConfigurationProperties("app.security.refresh")
public record RefreshTokenProperties(
    @NotNull Duration ttl,
    @NotBlank String cookieName,
    @NotBlank String cookiePath,
    boolean cookieSecure,
    @NotBlank String cookieSameSite,
    // Window in which presenting a just-rotated token is treated as a benign race (another tab)
    // rather than theft.
    @NotNull Duration reuseGrace) {}
