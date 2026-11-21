package com.careermatcher.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ProductionGuardTest {

  static final Map<String, Object> SAFE = Map.of(
      "app.environment", "production",
      "app.security.refresh.cookie-secure", "true",
      "springdoc.api-docs.enabled", "false",
      "springdoc.swagger-ui.enabled", "false",
      "app.cors.allowed-origins", "https://app.example.org",
      "app.rate-limit.enabled", "true",
      "server.forward-headers-strategy", "native");

  static StandardEnvironment env(Map<String, Object> base, Object... overrides) {
    Map<String, Object> m = new java.util.HashMap<>(base);
    for (int i = 0; i < overrides.length; i += 2) m.put((String) overrides[i], overrides[i + 1]);
    StandardEnvironment e = new StandardEnvironment();
    e.getPropertySources().addFirst(new MapPropertySource("test", m));
    return e;
  }

  static void fire(StandardEnvironment e) {
    new ProductionGuard().onApplicationEvent(
        new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), new SpringApplication(), new String[0], e));
  }

  @Test
  void safeProductionSettingsStart() {
    fire(env(SAFE));
    assertThat(ProductionGuard.problems(env(SAFE))).isEmpty();
  }

  @Test
  void devIsNeverBlocked() {
    fire(env(SAFE, "app.environment", "dev", "app.security.refresh.cookie-secure", "false"));
  }

  @Test
  void unsafeProductionSettingsAreRejected() {
    assertThatThrownBy(() -> fire(env(SAFE, "app.security.refresh.cookie-secure", "false")))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("COOKIE_SECURE");
    assertThat(ProductionGuard.problems(env(SAFE, "springdoc.api-docs.enabled", "true"))).anyMatch(p -> p.contains("OPENAPI"));
    assertThat(ProductionGuard.problems(env(SAFE, "app.cors.allowed-origins", "http://app.example.org"))).hasSize(1);
    assertThat(ProductionGuard.problems(env(SAFE, "app.cors.allowed-origins", "https://*.example.org"))).hasSize(1);
    assertThat(ProductionGuard.problems(env(SAFE, "app.cors.allowed-origins", "https://app.example.org,https://localhost:3000"))).hasSize(1);
    assertThat(ProductionGuard.problems(env(SAFE, "app.rate-limit.enabled", "false"))).hasSize(1);
    assertThat(ProductionGuard.problems(env(SAFE, "app.admin.password", "short-pass"))).hasSize(1);
    assertThat(ProductionGuard.problems(env(SAFE, "server.forward-headers-strategy", "none"))).hasSize(1);
  }

  @Test
  void localOriginsOnlyWithExplicitRehearsalFlag() {
    StandardEnvironment rehearsal = env(SAFE, "app.cors.allowed-origins", "https://app.localhost", "app.allow-local-origins", "true");
    assertThat(ProductionGuard.problems(rehearsal)).isEmpty();
    assertThat(ProductionGuard.warnings(rehearsal)).anyMatch(w -> w.contains("ALLOW_LOCAL_ORIGINS"));
    assertThat(ProductionGuard.problems(env(SAFE, "app.cors.allowed-origins", "https://app.localhost"))).hasSize(1);
  }

  @Test
  void demoDataIsAWarning() {
    assertThat(ProductionGuard.warnings(env(SAFE, "app.jobs.demo-data-enabled", "true"))).hasSize(1);
  }
}
