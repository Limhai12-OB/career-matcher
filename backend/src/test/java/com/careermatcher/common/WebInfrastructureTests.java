package com.careermatcher.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class WebInfrastructureTests {

  @Autowired MockMvc mvc;

  @Test
  void livenessIsPublicAndUp() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void readinessIncludesDatabaseAndIsUp() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components.db.status").value("UP"))
        // The AI service must not affect readiness.
        .andExpect(jsonPath("$.components.aiService").doesNotExist());
  }

  @Test
  void healthDoesNotLeakDetails() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(jsonPath("$.components.db.details").doesNotExist());
  }

  @Test
  void unknownApiRequiresAuthenticationAndReturnsProblemJson() throws Exception {
    mvc.perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
  }

  @Test
  void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  void openApiDocumentIsServed() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("AI Career Matcher API"));
  }

  @Test
  void requestIdIsGeneratedOrEchoed() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(header().exists("X-Request-Id"));
    mvc.perform(get("/actuator/health/liveness").header("X-Request-Id", "abc12345-test"))
        .andExpect(header().string("X-Request-Id", "abc12345-test"));
  }

  @Test
  void maliciousRequestIdIsReplaced() throws Exception {
    mvc.perform(get("/actuator/health/liveness").header("X-Request-Id", "bad\nid injected"))
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad\nid injected")));
  }

  @Test
  void corsAllowsConfiguredFrontendOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/anything")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void corsRejectsUnknownOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/anything")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}
