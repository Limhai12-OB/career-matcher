package com.careermatcher.aiclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Regression: requests to the AI service must be plain HTTP/1.1 without an h2c upgrade. */
class AiServiceClientConfigTest {

  @Test
  void analysisClientDoesNotAttemptHttp2Upgrade() throws Exception {
    AtomicReference<String> upgrade = new AtomicReference<>("unset");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ping",
        ex -> {
          upgrade.set(String.valueOf(ex.getRequestHeaders().getFirst("Upgrade")));
          ex.sendResponseHeaders(204, -1);
          ex.close();
        });
    server.start();
    try {
      var props =
          new AiServiceProperties(
              URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
              "t".repeat(40),
              Duration.ofSeconds(2),
              Duration.ofSeconds(2),
              Duration.ofSeconds(5));
      RestClient client = new AiServiceClientConfig().aiAnalysisRestClient(RestClient.builder(), props);
      client.post().uri("/ping").body("x").retrieve().toBodilessEntity();
      assertThat(upgrade.get()).isEqualTo("null");
    } finally {
      server.stop(0);
    }
  }
}
