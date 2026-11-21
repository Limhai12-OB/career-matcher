package com.careermatcher.aiclient;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiServiceClientConfig {

  public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  @Bean
  RestClient aiServiceRestClient(RestClient.Builder builder, AiServiceProperties props) {
    return build(builder, props, props.readTimeout());
  }

  @Bean
  RestClient aiAnalysisRestClient(RestClient.Builder builder, AiServiceProperties props) {
    return build(builder, props, props.analysisTimeout());
  }

  private static RestClient build(
      RestClient.Builder builder, AiServiceProperties props, java.time.Duration readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            // The AI service speaks plain HTTP/1.1; the JDK's default h2c upgrade attempt over
            // cleartext corrupts multipart uploads on servers that don't support it.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(props.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);

    RestClient.Builder configured =
        builder.clone().baseUrl(props.baseUrl().toString()).requestFactory(requestFactory);
    if (props.hasToken()) {
      configured.defaultHeader(INTERNAL_TOKEN_HEADER, props.internalToken());
    }
    return configured.build();
  }
}
