package com.careermatcher.auth;

import static com.careermatcher.support.TestApi.PASSWORD;
import static com.careermatcher.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestPropertySource(
    properties = {
      "app.rate-limit.enabled=true",
      "app.rate-limit.login-per-email.capacity=3",
      "app.rate-limit.register-per-ip.capacity=1000",
      "app.rate-limit.sensitive-account-action-per-user.capacity=2"
    })
class RateLimitTests {

  @Autowired MockMvc mvc;
  @Autowired TestApi api;

  @Test
  void repeatedFailedLoginsForOneAccountAreThrottled() throws Exception {
    Session s = api.register();
    String wrong = json("{'email':'%s','password':'Wrong-pass-123'}", s.email());
    for (int i = 0; i < 3; i++) {
      mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
          .andExpect(status().isUnauthorized());
    }
    // Even the correct password is refused while throttled.
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", s.email(), PASSWORD)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void sensitiveAccountActionsAreThrottledPerUser() throws Exception {
    Session s = api.register();
    String body = json("{'currentPassword':'Wrong-pass-123','newPassword':'Another-pass-99'}");
    api.post(s.accessToken(), "/api/v1/me/password", body).andExpect(status().isBadRequest());
    api.post(s.accessToken(), "/api/v1/me/password", body).andExpect(status().isBadRequest());
    api.post(s.accessToken(), "/api/v1/me/password", body)
        .andExpect(status().isTooManyRequests());
  }
}
