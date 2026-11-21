package com.careermatcher.aiclient;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Connection settings for the internal Python AI service. */
@Validated
@ConfigurationProperties("app.ai-service")
public record AiServiceProperties(
    @NotNull URI baseUrl,
    String internalToken,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("3s") Duration readTimeout,
    /** Read timeout for CV analysis calls (document parsing + LLM). */
    @DefaultValue("180s") Duration analysisTimeout) {

  public boolean hasToken() {
    return internalToken != null && !internalToken.isBlank();
  }

  @Override
  public String toString() {
    // Never expose the shared secret through logs or actuator.
    return "AiServiceProperties[baseUrl=" + baseUrl + ", internalToken=***]";
  }
}
