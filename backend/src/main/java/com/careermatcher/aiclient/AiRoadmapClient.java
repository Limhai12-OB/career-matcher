package com.careermatcher.aiclient;

import com.careermatcher.aiclient.dto.AiRoadmap;
import com.careermatcher.aiclient.dto.RoadmapFacts;
import jakarta.validation.Validator;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Requests a learning roadmap from the internal AI service. */
@Component
public class AiRoadmapClient {

  private final RestClient client;
  private final Validator validator;
  private final JsonMapper strict;

  public AiRoadmapClient(@Qualifier("aiAnalysisRestClient") RestClient client, Validator validator, JsonMapper jsonMapper) {
    this.client = client;
    this.validator = validator;
    this.strict = jsonMapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  record ErrorBody(String code, String message) {}

  public AiRoadmap roadmap(RoadmapFacts facts) {
    int status;
    byte[] body;
    try {
      Object[] r = client.post().uri("/v1/roadmap").contentType(MediaType.APPLICATION_JSON).body(facts)
          .exchange((req, res) -> new Object[] {res.getStatusCode().value(), res.getBody().readAllBytes()});
      status = (int) r[0];
      body = (byte[]) r[1];
    } catch (ResourceAccessException e) {
      Throwable c = e.getCause();
      throw new AiAnalysisException(
          c instanceof SocketTimeoutException || c instanceof HttpTimeoutException ? "AI_TIMEOUT" : "AI_UNAVAILABLE",
          "AI service unreachable", e);
    }
    if (status != 200) {
      String code = "AI_UNAVAILABLE";
      try {
        ErrorBody err = strict.readValue(body, ErrorBody.class);
        if (err.code() != null) code = err.code();
      } catch (JacksonException ignored) {
        // keep AI_UNAVAILABLE
      }
      throw new AiAnalysisException(code, "Roadmap failed");
    }
    try {
      AiRoadmap r = strict.readValue(body, AiRoadmap.class);
      if (!validator.validate(r).isEmpty()) {
        throw new AiAnalysisException("AI_INVALID_OUTPUT", "Roadmap failed validation");
      }
      return r;
    } catch (JacksonException e) {
      throw new AiAnalysisException("AI_INVALID_OUTPUT", "Malformed roadmap", e);
    }
  }
}
