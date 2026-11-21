package com.careermatcher.support;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.aiclient.AiCvAnalysisClient;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Shared context for CV tests: the AI service is replaced by a Mockito mock. */
@IntegrationTest
public abstract class CvTestBase {

  @MockitoBean protected AiCvAnalysisClient aiClient;
  @Autowired protected MockMvc mvc;
  @Autowired protected TestApi api;

  protected ResultActions upload(String token, String filename, byte[] content) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.multipart("/api/v1/me/cvs")
            .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  protected UUID uploadAccepted(String token, String filename, byte[] content) throws Exception {
    String body =
        upload(token, filename, content).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(JsonPath.read(body, "$.id"));
  }

  /** Polls until the analysis reaches a terminal status and returns the detail JSON. */
  protected String awaitTerminal(String token, UUID analysisId) {
    String[] holder = new String[1];
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              holder[0] =
                  api.get(token, "/api/v1/me/cv-analyses/" + analysisId)
                      .andReturn()
                      .getResponse()
                      .getContentAsString();
              String s = JsonPath.read(holder[0], "$.status");
              return s.equals("COMPLETED") || s.equals("FAILED");
            });
    return holder[0];
  }
}
