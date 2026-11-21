package com.careermatcher.aiclient;

import com.careermatcher.aiclient.dto.AiCvAnalysis;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Sends a CV to the internal AI service and returns a validated analysis. */
@Component
public class AiCvAnalysisClient {

  private static final Logger log = LoggerFactory.getLogger(AiCvAnalysisClient.class);

  private final RestClient client;
  private final Validator validator;
  private final JsonMapper strictMapper;

  public AiCvAnalysisClient(
      @Qualifier("aiAnalysisRestClient") RestClient client, Validator validator, JsonMapper jsonMapper) {
    this.client = client;
    this.validator = validator;
    // Unknown or malformed fields mean the contract was broken: reject rather than ignore.
    this.strictMapper =
        jsonMapper
            .rebuild()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
  }

  /** Error body returned by the AI service. */
  record ErrorBody(String code, String message) {}

  public AiCvAnalysis analyze(byte[] content, String filenameHint) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "file",
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return filenameHint; // informational only; the AI service sniffs content
          }
        });
    byte[] body;
    int status;
    try {
      var response =
          client
              .post()
              .uri("/v1/cv/analyze")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .headers(h -> {
                String requestId = MDC.get("requestId");
                if (requestId != null) {
                  h.set("X-Request-Id", requestId);
                }
              })
              .body(form)
              .exchange(
                  (req, res) ->
                      new Object[] {res.getStatusCode().value(), res.getBody().readAllBytes()});
      status = (int) response[0];
      body = (byte[]) response[1];
    } catch (ResourceAccessException e) {
      Throwable cause = e.getCause();
      if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
        throw new AiAnalysisException("AI_TIMEOUT", "AI service timed out", e);
      }
      throw new AiAnalysisException("AI_UNAVAILABLE", "AI service unreachable", e);
    }

    if (status != 200) {
      String code = "AI_UNAVAILABLE";
      try {
        ErrorBody err = strictMapper.readValue(body, ErrorBody.class);
        if (err.code() != null) {
          code = err.code();
        }
      } catch (JacksonException ignored) {
        // Non-JSON error body (e.g. proxy error page): keep AI_UNAVAILABLE.
      }
      log.warn("AI analysis failed status={} code={}", status, code);
      throw new AiAnalysisException(code, "AI analysis failed");
    }

    AiCvAnalysis analysis;
    try {
      analysis = strictMapper.readValue(body, AiCvAnalysis.class);
    } catch (JacksonException e) {
      // Type only: Jackson messages can quote values from the response (CV content).
      log.warn("AI analysis response could not be parsed: {}", e.getClass().getSimpleName());
      throw new AiAnalysisException("AI_INVALID_OUTPUT", "Malformed AI response", e);
    }
    Set<ConstraintViolation<AiCvAnalysis>> violations = validator.validate(analysis);
    if (!violations.isEmpty()) {
      // Field paths only; values may contain CV content.
      log.warn(
          "AI analysis response failed validation at: {}",
          violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
      throw new AiAnalysisException("AI_INVALID_OUTPUT", "AI response failed validation");
    }
    return analysis;
  }
}
