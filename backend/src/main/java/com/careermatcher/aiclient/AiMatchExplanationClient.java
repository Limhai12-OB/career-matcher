package com.careermatcher.aiclient;

import com.careermatcher.aiclient.dto.AiMatchExplanation;
import com.careermatcher.aiclient.dto.MatchFacts;
import jakarta.validation.Validator;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Requests a match explanation from the internal AI service. */
@Component
public class AiMatchExplanationClient {

  private static final Logger log = LoggerFactory.getLogger(AiMatchExplanationClient.class);

  private final RestClient client;
  private final Validator validator;
  private final JsonMapper strict;

  public AiMatchExplanationClient(
      @Qualifier("aiAnalysisRestClient") RestClient client, Validator validator, JsonMapper jsonMapper) {
    this.client = client;
    this.validator = validator;
    this.strict = jsonMapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  record ErrorBody(String code, String message) {}

  public AiMatchExplanation explain(MatchFacts facts) {
    int status;
    byte[] body;
    try {
      Object[] r =
          client
              .post()
              .uri("/v1/match/explain")
              .contentType(MediaType.APPLICATION_JSON)
              .body(facts)
              .exchange((req, res) -> new Object[] {res.getStatusCode().value(), res.getBody().readAllBytes()});
      status = (int) r[0];
      body = (byte[]) r[1];
    } catch (ResourceAccessException e) {
      Throwable cause = e.getCause();
      String code = cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException ? "AI_TIMEOUT" : "AI_UNAVAILABLE";
      throw new AiAnalysisException(code, "AI service unreachable", e);
    }
    if (status != 200) {
      String code = "AI_UNAVAILABLE";
      try {
        ErrorBody err = strict.readValue(body, ErrorBody.class);
        if (err.code() != null) code = err.code();
      } catch (JacksonException ignored) {
        // keep AI_UNAVAILABLE
      }
      log.warn("Match explanation failed status={} code={}", status, code);
      throw new AiAnalysisException(code, "Explanation failed");
    }
    try {
      AiMatchExplanation result = strict.readValue(body, AiMatchExplanation.class);
      if (!validator.validate(result).isEmpty()) {
        throw new AiAnalysisException("AI_INVALID_OUTPUT", "Explanation failed validation");
      }
      return result;
    } catch (JacksonException e) {
      throw new AiAnalysisException("AI_INVALID_OUTPUT", "Malformed explanation", e);
    }
  }
}
