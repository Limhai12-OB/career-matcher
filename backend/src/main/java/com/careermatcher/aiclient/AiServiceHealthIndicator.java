package com.careermatcher.aiclient;

import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies the backend can reach the AI service <em>and</em> authenticate with the shared internal
 * token, by calling its token-protected status endpoint. Registered as health component {@code
 * aiService}; it is part of the {@code dependencies} group but deliberately not of {@code
 * readiness}, so the backend keeps serving non-AI features when the AI service is down.
 */
@Component
public class AiServiceHealthIndicator implements HealthIndicator {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient aiServiceRestClient;
  private final AiServiceProperties props;

  public AiServiceHealthIndicator(
      @org.springframework.beans.factory.annotation.Qualifier("aiServiceRestClient") RestClient aiServiceRestClient,
      AiServiceProperties props) {
    this.aiServiceRestClient = aiServiceRestClient;
    this.props = props;
  }

  @Override
  public Health health() {
    if (!props.hasToken()) {
      return Health.down().withDetail("reason", "AI_INTERNAL_TOKEN not configured").build();
    }
    try {
      Map<String, Object> body =
          aiServiceRestClient.get().uri("/v1/status").retrieve().body(MAP_TYPE);
      Object status = body == null ? null : body.get("status");
      return "ok".equals(status)
          ? Health.up().build()
          : Health.down().withDetail("reason", "unexpected status payload").build();
    } catch (RestClientException ex) {
      return Health.down().withDetail("reason", ex.getClass().getSimpleName()).build();
    }
  }
}
