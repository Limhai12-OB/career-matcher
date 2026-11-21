package com.careermatcher.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Small DSL over MockMvc for authenticated API calls in integration tests. */
@TestComponent
public class TestApi {

  public static final String PASSWORD = "Correct-Horse-42";
  public static final String REFRESH_COOKIE = "cm_refresh";
  private static final AtomicInteger SEQ = new AtomicInteger();

  private final MockMvc mvc;

  public TestApi(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** An authenticated session: access token, refresh cookie value, user id. */
  public record Session(String email, String accessToken, String refreshToken, UUID userId) {}

  public static String uniqueEmail() {
    return "user" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8)
        + "@example.com";
  }

  public static String json(String template, Object... args) {
    return template.formatted(args).replace('\'', '"');
  }

  public Session register() throws Exception {
    return register(uniqueEmail(), "Test User");
  }

  public Session register(String email, String fullName) throws Exception {
    MockHttpServletResponse res =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            "{'email':'%s','password':'%s','fullName':'%s'}",
                            email, PASSWORD, fullName)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    return session(email, res);
  }

  public Session login(String email, String password) throws Exception {
    MockHttpServletResponse res =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("{'email':'%s','password':'%s'}", email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return session(email, res);
  }

  public static Session session(String email, MockHttpServletResponse res) throws Exception {
    String body = res.getContentAsString();
    String token = JsonPath.read(body, "$.accessToken");
    String id = JsonPath.read(body, "$.user.id");
    return new Session(email, token, refreshCookieValue(res), UUID.fromString(id));
  }

  /** Extracts the refresh cookie value from Set-Cookie headers (empty string if cleared). */
  public static String refreshCookieValue(MockHttpServletResponse res) {
    return res.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(h -> h.startsWith(REFRESH_COOKIE + "="))
        .map(h -> h.substring(REFRESH_COOKIE.length() + 1, h.indexOf(';')))
        .findFirst()
        .orElse(null);
  }

  public static String refreshSetCookieHeader(MockHttpServletResponse res) {
    return res.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(h -> h.startsWith(REFRESH_COOKIE + "="))
        .findFirst()
        .orElse(null);
  }

  public ResultActions refresh(String refreshToken) throws Exception {
    MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post("/api/v1/auth/refresh");
    if (refreshToken != null) {
      req.cookie(new Cookie(REFRESH_COOKIE, refreshToken));
    }
    return mvc.perform(req);
  }

  public ResultActions get(String token, String path) throws Exception {
    return mvc.perform(withAuth(MockMvcRequestBuilders.get(path), token));
  }

  public ResultActions put(String token, String path, String body) throws Exception {
    return mvc.perform(
        withAuth(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(body), token));
  }

  public ResultActions post(String token, String path, String body) throws Exception {
    MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post(path);
    if (body != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(withAuth(req, token));
  }

  public ResultActions patch(String token, String path, String body) throws Exception {
    return mvc.perform(
        withAuth(
            MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON).content(body), token));
  }

  public ResultActions delete(String token, String path, String body) throws Exception {
    return mvc.perform(
        withAuth(MockMvcRequestBuilders.delete(path).contentType(MediaType.APPLICATION_JSON).content(body), token));
  }

  private static MockHttpServletRequestBuilder withAuth(
      MockHttpServletRequestBuilder req, String token) {
    return token == null ? req : req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
