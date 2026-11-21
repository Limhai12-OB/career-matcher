package com.careermatcher.auth;

import static com.careermatcher.support.TestApi.PASSWORD;
import static com.careermatcher.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import com.careermatcher.support.TestTokens;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthFlowTests {

  @Autowired MockMvc mvc;
  @Autowired TestApi api;
  @Autowired JdbcTemplate jdbc;

  // ---- registration ----

  @Test
  void registerCreatesAccountProfileAndSession() throws Exception {
    String email = TestApi.uniqueEmail();
    MockHttpServletResponse res =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            "{'email':'%s','password':'%s','fullName':'  Ada Lovelace '}",
                            email.toUpperCase(), PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.email").value(email)) // normalized to lower case
            .andExpect(jsonPath("$.user.role").value("CANDIDATE"))
            .andExpect(jsonPath("$.user.onboardingCompleted").value(false))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn()
            .getResponse();

    String cookie = TestApi.refreshSetCookieHeader(res);
    assertThat(cookie)
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/api/v1/auth")
        .contains("Max-Age=");
    Session s = TestApi.session(email, res);
    api.get(s.accessToken(), "/api/v1/me/profile")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));
  }

  @Test
  void passwordIsStoredAsArgon2HashNeverPlaintext() throws Exception {
    Session s = api.register();
    String hash =
        jdbc.queryForObject("select password_hash from users where id = ?", String.class, s.userId());
    assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(PASSWORD);
  }

  @Test
  void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
    String email = TestApi.uniqueEmail();
    api.register(email, "First");
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "{'email':'%s','password':'%s','fullName':'Second'}",
                        email.toUpperCase(), PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void registerValidatesInputWithFieldErrors() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'not-an-email','password':'short','fullName':''}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='fullName')]").exists());
  }

  @Test
  void passwordPolicyRequiresLetterAndDigit() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "{'email':'%s','password':'onlyletterspassword','fullName':'Al'}",
                        TestApi.uniqueEmail())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void malformedJsonReturnsProblem() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // ---- login ----

  @Test
  void loginSucceedsWithCorrectCredentials() throws Exception {
    Session reg = api.register();
    Session s = api.login(reg.email().toUpperCase(), PASSWORD);
    assertThat(s.userId()).isEqualTo(reg.userId());
    assertThat(s.refreshToken()).isNotBlank().isNotEqualTo(reg.refreshToken());
  }

  @Test
  void concurrentLoginsToTheSameAccountAllSucceed() throws Exception {
    Session reg = api.register();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
    try {
      var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 6; i++) {
        results.add(
            pool.submit(
                () ->
                    mvc.perform(
                            post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'email':'%s','password':'%s'}", reg.email(), PASSWORD)))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
      }
      for (var r : results) {
        assertThat(r.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdown();
    }
    assertThat(
            jdbc.queryForObject("select last_login_at from users where id = ?", java.sql.Timestamp.class, reg.userId()))
        .isNotNull();
  }

  @Test
  void loginFailuresAreIndistinguishable() throws Exception {
    Session reg = api.register();
    for (String body :
        new String[] {
          json("{'email':'%s','password':'Wrong-password-1'}", reg.email()),
          json("{'email':'%s','password':'Wrong-password-1'}", TestApi.uniqueEmail())
        }) {
      mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
          .andExpect(jsonPath("$.detail").value("Invalid email or password."))
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
  }

  @Test
  void publicAuthEndpointsIgnoreStaleBearerTokens() throws Exception {
    Session reg = api.register();
    mvc.perform(
            post("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer garbage.token.value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", reg.email(), PASSWORD)))
        .andExpect(status().isOk());
  }

  // ---- access tokens ----

  @Test
  void meReturnsCurrentUser() throws Exception {
    Session s = api.register();
    api.get(s.accessToken(), "/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(s.userId().toString()))
        .andExpect(jsonPath("$.email").value(s.email()));
  }

  @Test
  void protectedEndpointsRejectMissingOrInvalidTokens() throws Exception {
    Session s = api.register();
    Instant now = Instant.now();
    String expired =
        TestTokens.sign(
            TestTokens.TEST_SECRET,
            s.userId(),
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            0);
    String wrongKey =
        TestTokens.sign(
            "another-secret-0123456789abcdef0123456789abcdef",
            s.userId(),
            now,
            now.plusSeconds(600),
            0);
    String noVersion =
        TestTokens.sign(TestTokens.TEST_SECRET, s.userId(), now, now.plusSeconds(600), null);
    String unknownUser =
        TestTokens.sign(TestTokens.TEST_SECRET, UUID.randomUUID(), now, now.plusSeconds(600), 0);

    api.get(null, "/api/v1/auth/me")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    for (String bad :
        new String[] {
          "not-a-jwt", expired, wrongKey, noVersion, unknownUser, TestTokens.unsigned(s.userId())
        }) {
      api.get(bad, "/api/v1/auth/me")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
  }

  // ---- refresh ----

  @Test
  void refreshRotatesTokenAndIssuesNewAccessToken() throws Exception {
    Session s = api.register();
    MockHttpServletResponse res =
        api.refresh(s.refreshToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").value(s.userId().toString()))
            .andReturn()
            .getResponse();
    String rotated = TestApi.refreshCookieValue(res);
    assertThat(rotated).isNotBlank().isNotEqualTo(s.refreshToken());
    api.refresh(rotated).andExpect(status().isOk());
  }

  @Test
  void reusingRotatedRefreshTokenRevokesTheWholeSession() throws Exception {
    Session s = api.register();
    String rotated = TestApi.refreshCookieValue(api.refresh(s.refreshToken()).andReturn().getResponse());

    // Attacker (or stale client) replays the old token -> rejected and session family revoked.
    api.refresh(s.refreshToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    // The legitimate holder's newer token is now revoked too.
    api.refresh(rotated).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutOrWithUnknownCookieFails() throws Exception {
    api.refresh(null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    api.refresh("unknown-token-value").andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRejectsForeignOrigin() throws Exception {
    Session s = api.register();
    mvc.perform(
            post("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .cookie(new Cookie(TestApi.REFRESH_COOKIE, s.refreshToken())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .cookie(new Cookie(TestApi.REFRESH_COOKIE, s.refreshToken())))
        .andExpect(status().isOk());
  }

  // ---- logout ----

  @Test
  void logoutRevokesSessionAndClearsCookie() throws Exception {
    Session s = api.register();
    mvc.perform(
            post("/api/v1/auth/logout").cookie(new Cookie(TestApi.REFRESH_COOKIE, s.refreshToken())))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    api.refresh(s.refreshToken()).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutOnlyEndsTheCurrentSession() throws Exception {
    Session first = api.register();
    Session second = api.login(first.email(), PASSWORD);
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie(TestApi.REFRESH_COOKIE, first.refreshToken())))
        .andExpect(status().isNoContent());
    api.refresh(second.refreshToken()).andExpect(status().isOk());
  }

  @Test
  void logoutWithoutCookieIsHarmless() throws Exception {
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
  }

  @Test
  void accessTokenIsNotAcceptedAsRefreshCookie() throws Exception {
    Session s = api.register();
    api.refresh(s.accessToken()).andExpect(status().isUnauthorized());
  }

  @Test
  void sensitiveResponsesAreNotCached() throws Exception {
    Session s = api.register();
    mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken()))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))));
  }
}
