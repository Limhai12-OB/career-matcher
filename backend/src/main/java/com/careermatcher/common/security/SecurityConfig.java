package com.careermatcher.common.security;

import com.careermatcher.common.config.CorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security: deny by default, JWT bearer access tokens, role-based rules for admin
 * paths. Per-resource ownership is enforced in services by scoping every query to the
 * authenticated user's id.
 *
 * <p>CSRF: no cookie authenticates API requests (access tokens travel in the Authorization
 * header). The only cookie, the refresh token, is {@code SameSite=Strict}, scoped to {@code
 * /api/v1/auth}, and its endpoints additionally verify the {@code Origin} header (see {@code
 * OriginVerifier}).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /** Endpoints that must work without (and must ignore) an access token. */
  static final String[] PUBLIC_AUTH_PATHS = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
  };

  private static final String[] PUBLIC_PATHS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info",
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
    "/error"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, AccessTokenAuthenticationConverter tokenConverter) throws Exception {
    AuthenticationEntryPoint entryPoint = problemEntryPoint();
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/api/**")
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS)
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    // Matching needs a candidate profile; admins have none.
                    .requestMatchers("/api/v1/me/matches", "/api/v1/me/matches/**")
                    .hasRole("CANDIDATE")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.bearerTokenResolver(bearerTokenResolver())
                    .authenticationEntryPoint(entryPoint)
                    .jwt(j -> j.jwtAuthenticationConverter(tokenConverter)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            ProblemJsonWriter.write(
                                res, HttpStatus.FORBIDDEN.value(), "Forbidden", "ACCESS_DENIED")));
    return http.build();
  }

  private static AuthenticationEntryPoint problemEntryPoint() {
    return (req, res, ex) -> {
      boolean invalidToken = ex instanceof OAuth2AuthenticationException;
      res.setHeader(
          HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
      ProblemJsonWriter.write(
          res,
          HttpStatus.UNAUTHORIZED.value(),
          "Unauthorized",
          invalidToken ? "INVALID_TOKEN" : "AUTH_REQUIRED");
    };
  }

  /**
   * Ignores any Authorization header on the public auth endpoints, so that a stale access token
   * held by the client cannot break login, registration, refresh or logout.
   */
  private static BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    List<String> publicPaths = List.of(PUBLIC_AUTH_PATHS);
    return (HttpServletRequest request) ->
        publicPaths.contains(request.getRequestURI()) ? null : delegate.resolve(request);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    // Argon2id; delegating format ({argon2}...) allows transparent algorithm upgrades later.
    String id = "argon2";
    return new DelegatingPasswordEncoder(
        id, Map.of(id, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(props.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Request-Id"));
    config.setExposedHeaders(List.of("X-Request-Id", HttpHeaders.RETRY_AFTER));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
