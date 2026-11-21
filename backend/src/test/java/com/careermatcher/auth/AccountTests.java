package com.careermatcher.auth;

import static com.careermatcher.support.TestApi.PASSWORD;
import static com.careermatcher.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AccountTests {

  private static final String NEW_PASSWORD = "Brand-new-pass-7";

  @Autowired MockMvc mvc;
  @Autowired TestApi api;
  @Autowired JdbcTemplate jdbc;

  @Test
  void changePasswordRequiresCorrectCurrentPassword() throws Exception {
    Session s = api.register();
    api.post(
            s.accessToken(),
            "/api/v1/me/password",
            json("{'currentPassword':'Wrong-pass-123','newPassword':'%s'}", NEW_PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
  }

  @Test
  void changePasswordEnforcesPolicyAndDifference() throws Exception {
    Session s = api.register();
    api.post(
            s.accessToken(),
            "/api/v1/me/password",
            json("{'currentPassword':'%s','newPassword':'weak'}", PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    api.post(
            s.accessToken(),
            "/api/v1/me/password",
            json("{'currentPassword':'%s','newPassword':'%s'}", PASSWORD, PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
  }

  @Test
  void changePasswordInvalidatesAllExistingSessionsAndTokens() throws Exception {
    Session s = api.register();
    Session otherDevice = api.login(s.email(), PASSWORD);

    MockHttpServletResponse res =
        api.post(
                s.accessToken(),
                "/api/v1/me/password",
                json("{'currentPassword':'%s','newPassword':'%s'}", PASSWORD, NEW_PASSWORD))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Session fresh = TestApi.session(s.email(), res);

    // Old access tokens are rejected immediately (token version bumped).
    api.get(s.accessToken(), "/api/v1/auth/me").andExpect(status().isUnauthorized());
    api.get(otherDevice.accessToken(), "/api/v1/auth/me").andExpect(status().isUnauthorized());
    // Old refresh tokens are revoked.
    api.refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    api.refresh(otherDevice.refreshToken()).andExpect(status().isUnauthorized());
    // The caller keeps a working session.
    api.get(fresh.accessToken(), "/api/v1/auth/me").andExpect(status().isOk());
    api.refresh(fresh.refreshToken()).andExpect(status().isOk());
    // Credentials updated.
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", s.email(), PASSWORD)))
        .andExpect(status().isUnauthorized());
    api.login(s.email(), NEW_PASSWORD);
  }

  @Test
  void deleteAccountRequiresPassword() throws Exception {
    Session s = api.register();
    api.delete(s.accessToken(), "/api/v1/me", json("{'password':'Wrong-pass-123'}"))
        .andExpect(status().isBadRequest());
    api.get(s.accessToken(), "/api/v1/me/profile").andExpect(status().isOk());
  }

  @Test
  void deleteAccountRemovesAllDataAndInvalidatesTokens() throws Exception {
    Session s = api.register();
    api.delete(s.accessToken(), "/api/v1/me", json("{'password':'%s'}", PASSWORD))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    api.get(s.accessToken(), "/api/v1/me/profile").andExpect(status().isUnauthorized());
    api.refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    for (String table :
        new String[] {"users where id", "candidate_profiles where user_id",
          "candidate_preferences where user_id", "refresh_tokens where user_id"}) {
      Integer count =
          jdbc.queryForObject("select count(*) from " + table + " = ?", Integer.class, s.userId());
      assertThat(count).as(table).isZero();
    }
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", s.email(), PASSWORD)))
        .andExpect(status().isUnauthorized());
  }
}
