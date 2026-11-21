package com.careermatcher.aiclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServiceHealthIndicatorTest {

  private static final String TOKEN = "test-token-0123456789abcdef0123456789";

  private static AiServiceProperties props(String token) {
    return new AiServiceProperties(
        URI.create("http://ai.test"), token, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5));
  }

  @Test
  void downWhenTokenMissing() {
    var indicator = new AiServiceHealthIndicator(RestClient.create(), props(""));
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void upWhenStatusEndpointRespondsOkWithToken() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://ai.test")
            .defaultHeader(AiServiceClientConfig.INTERNAL_TOKEN_HEADER, TOKEN);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/v1/status"))
        .andExpect(header(AiServiceClientConfig.INTERNAL_TOKEN_HEADER, TOKEN))
        .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

    var indicator = new AiServiceHealthIndicator(builder.build(), props(TOKEN));

    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    server.verify();
  }

  @Test
  void downWhenServiceErrors() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://ai.test/v1/status")).andRespond(withServerError());

    var indicator = new AiServiceHealthIndicator(builder.build(), props(TOKEN));

    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("reason");
  }

  @Test
  void toStringMasksToken() {
    assertThat(props(TOKEN).toString()).doesNotContain(TOKEN);
  }
}
