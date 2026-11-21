package com.careermatcher.aiclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import jakarta.validation.Validation;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** The backend re-validates everything the AI service returns before it can be persisted. */
class AiCvAnalysisClientTest {

  static final String VALID =
      """
      {"schemaVersion":"cv-analysis-v1","provider":"anthropic","model":"claude-opus-5-5",
       "promptVersion":"cv-extract-v1","document":{"type":"PDF","pages":1,"characters":900},
       "skills":[{"name":"Java","category":"PROGRAMMING_LANGUAGE","evidence":"Java, Spring"}],
       "education":[],"experience":[{"title":"Intern","organization":"ABC","employmentType":"INTERNSHIP",
         "startDate":"2024-06","endDate":null,"isCurrent":true,"skills":["Java"],"evidence":"Intern at ABC"}],
       "projects":[],"certifications":[],
       "droppedItems":{"skills":0,"education":0,"experience":0,"projects":0,"certifications":0},
       "warnings":["LIMITED_EXTRACTION"]}
      """;

  private MockRestServiceServer server;

  private AiCvAnalysisClient client() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    server = MockRestServiceServer.bindTo(builder).build();
    return new AiCvAnalysisClient(
        builder.build(),
        Validation.buildDefaultValidatorFactory().getValidator(),
        JsonMapper.builder().build());
  }

  private void respond(String body) {
    server
        .expect(requestTo("http://ai.test/v1/cv/analyze"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  private String code(Runnable call) {
    try {
      call.run();
    } catch (AiAnalysisException e) {
      return e.getCode();
    }
    throw new AssertionError("expected failure");
  }

  @Test
  void parsesValidResponse() {
    AiCvAnalysisClient c = client();
    respond(VALID);
    var result = c.analyze(new byte[] {1}, "cv.pdf");
    assertThat(result.skills()).hasSize(1);
    assertThat(result.experience().get(0).isCurrent()).isTrue();
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "{}",
        // unknown field (contract drift / injected key)
        "UNKNOWN",
        // invalid enum value
        "ENUM",
        // date not matching YYYY or YYYY-MM
        "DATE",
        // blank evidence
        "EVIDENCE",
        // wrong schema version
        "SCHEMA",
      })
  void rejectsInvalidResponses(String variant) {
    String body =
        switch (variant) {
          case "UNKNOWN" -> VALID.replace("\"warnings\"", "\"admin\":true,\"warnings\"");
          case "ENUM" -> VALID.replace("PROGRAMMING_LANGUAGE", "HACKER");
          case "DATE" -> VALID.replace("2024-06", "June 2024");
          case "EVIDENCE" -> VALID.replace("\"Java, Spring\"", "\"  \"");
          case "SCHEMA" -> VALID.replace("cv-analysis-v1", "cv-analysis-v9");
          default -> variant;
        };
    AiCvAnalysisClient c = client();
    respond(body);
    assertThat(code(() -> c.analyze(new byte[] {1}, "cv.pdf"))).isEqualTo("AI_INVALID_OUTPUT");
  }

  @Test
  void rejectsTooManySkills() {
    StringBuilder skills = new StringBuilder();
    for (int i = 0; i < 151; i++) {
      skills.append(i == 0 ? "" : ",").append("{\"name\":\"S").append(i)
          .append("\",\"category\":\"OTHER\",\"evidence\":\"e\"}");
    }
    AiCvAnalysisClient c = client();
    respond(VALID.replace("[{\"name\":\"Java\",\"category\":\"PROGRAMMING_LANGUAGE\",\"evidence\":\"Java, Spring\"}]", "[" + skills + "]"));
    assertThat(code(() -> c.analyze(new byte[] {1}, "cv.pdf"))).isEqualTo("AI_INVALID_OUTPUT");
  }

  @Test
  void mapsErrorResponses() {
    AiCvAnalysisClient c = client();
    server.expect(requestTo("http://ai.test/v1/cv/analyze"))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"NO_EXTRACTABLE_TEXT\",\"message\":\"x\"}"));
    assertThat(code(() -> c.analyze(new byte[] {1}, "cv.pdf"))).isEqualTo("NO_EXTRACTABLE_TEXT");

    AiCvAnalysisClient c2 = client();
    server.expect(requestTo("http://ai.test/v1/cv/analyze"))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>proxy</html>"));
    assertThat(code(() -> c2.analyze(new byte[] {1}, "cv.pdf"))).isEqualTo("AI_UNAVAILABLE");

    AiCvAnalysisClient c3 = client();
    server.expect(requestTo("http://ai.test/v1/cv/analyze"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"AI_RATE_LIMITED\",\"message\":\"x\"}"));
    assertThat(code(() -> c3.analyze(new byte[] {1}, "cv.pdf"))).isEqualTo("AI_RATE_LIMITED");
  }

  @Test
  void mapsTimeouts() {
    AiCvAnalysisClient c = client();
    server.expect(requestTo("http://ai.test/v1/cv/analyze"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));
    assertThatThrownBy(() -> c.analyze(new byte[] {1}, "cv.pdf"))
        .isInstanceOf(AiAnalysisException.class)
        .extracting(e -> ((AiAnalysisException) e).getCode())
        .isEqualTo("AI_TIMEOUT");
  }
}
