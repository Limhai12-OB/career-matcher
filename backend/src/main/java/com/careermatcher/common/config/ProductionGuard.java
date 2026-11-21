package com.careermatcher.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start with unsafe settings when {@code APP_ENV=production}. Runs before any bean is
 * created (registered in META-INF/spring.factories), so a misconfigured production deployment
 * never serves a request.
 */
public class ProductionGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    ConfigurableEnvironment env = event.getEnvironment();
    if (!"production".equalsIgnoreCase(env.getProperty("app.environment", "dev"))) {
      return;
    }
    List<String> problems = problems(env);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
    }
    warnings(env).forEach(w -> log.warn("Production configuration: {}", w));
  }

  static List<String> problems(ConfigurableEnvironment env) {
    List<String> p = new ArrayList<>();
    if (!env.getProperty("app.security.refresh.cookie-secure", Boolean.class, true)) {
      p.add("COOKIE_SECURE must be true (refresh cookies over HTTPS only)");
    }
    if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
        || env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
      p.add("OPENAPI_ENABLED must be false");
    }
    boolean allowLocal = env.getProperty("app.allow-local-origins", Boolean.class, false);
    String origins = env.getProperty("app.cors.allowed-origins", "");
    for (String o : origins.split(",")) {
      String origin = o.trim();
      if (origin.isEmpty()) continue;
      if (!origin.startsWith("https://") || origin.contains("*") || (!allowLocal && isLocal(origin))) {
        p.add("CORS_ALLOWED_ORIGINS must list only public https:// origins (found " + origin + ")");
      }
    }
    if (!env.getProperty("app.rate-limit.enabled", Boolean.class, true)) {
      p.add("RATE_LIMIT_ENABLED must be true");
    }
    if ("none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy", "none"))) {
      p.add("FORWARD_HEADERS_STRATEGY must be native (behind the HTTPS reverse proxy); otherwise all"
          + " clients share the proxy's IP for rate limiting");
    }
    String password = env.getProperty("app.admin.password", "");
    if (!password.isEmpty() && password.length() < 16) {
      p.add("ADMIN_PASSWORD must be at least 16 characters (or unset after the first start)");
    }
    return p;
  }

  static List<String> warnings(ConfigurableEnvironment env) {
    List<String> w = new ArrayList<>();
    if (env.getProperty("app.jobs.demo-data-enabled", Boolean.class, false)) {
      w.add("JOBS_DEMO_DATA_ENABLED=true: fictional DEMO listings are shown (labelled) to users");
    }
    if (env.getProperty("app.allow-local-origins", Boolean.class, false)) {
      w.add("ALLOW_LOCAL_ORIGINS=true: localhost origins accepted; for local production rehearsal only");
    }
    return w;
  }

  /** Loopback or unparsable origins, e.g. https://localhost or https://app.localhost:8443. */
  static boolean isLocal(String origin) {
    try {
      String host = URI.create(origin).getHost();
      return host == null || host.equals("localhost") || host.endsWith(".localhost")
          || host.equals("127.0.0.1") || host.equals("[::1]");
    } catch (IllegalArgumentException e) {
      return true;
    }
  }
}
