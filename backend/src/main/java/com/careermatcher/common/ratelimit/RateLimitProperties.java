package com.careermatcher.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** In-memory rate limits (single-instance MVP). Each rule allows {@code capacity} per {@code period}. */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @Valid @NotNull Rule loginPerIp,
    @Valid @NotNull Rule loginPerEmail,
    @Valid @NotNull Rule registerPerIp,
    @Valid @NotNull Rule refreshPerIp,
    @Valid @NotNull Rule sensitiveAccountActionPerUser,
    @Valid @NotNull Rule cvUploadPerUser,
    @Valid @NotNull Rule matchExplanationPerUser) {

  public record Rule(@Positive int capacity, @NotNull Duration period) {}
}
