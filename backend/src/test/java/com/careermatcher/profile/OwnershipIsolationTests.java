package com.careermatcher.profile;

import static com.careermatcher.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import com.careermatcher.support.TestTokens;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies that one user can never read or modify another user's profile data. */
@IntegrationTest
class OwnershipIsolationTests {

  @Autowired TestApi api;
  @Autowired JdbcTemplate jdbc;

  @Test
  void eachUserOnlySeesAndEditsTheirOwnProfile() throws Exception {
    Session alice = api.register(TestApi.uniqueEmail(), "Alice");
    Session bob = api.register(TestApi.uniqueEmail(), "Bob");

    api.put(
            alice.accessToken(),
            "/api/v1/me/profile",
            json("{'fullName':'Alice Private','headline':'Alice secret headline'}"))
        .andExpect(status().isOk());

    // Bob reads: gets his own data, never Alice's.
    api.get(bob.accessToken(), "/api/v1/me/profile")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Bob"))
        .andExpect(jsonPath("$.headline").doesNotExist());

    // Bob writes: only his row changes.
    api.put(bob.accessToken(), "/api/v1/me/profile", json("{'fullName':'Bob Updated'}"))
        .andExpect(status().isOk());
    api.put(
            bob.accessToken(),
            "/api/v1/me/preferences",
            json("{'desiredRoles':['Bob role'],'remotePreference':'REMOTE'}"))
        .andExpect(status().isOk());

    api.get(alice.accessToken(), "/api/v1/me/profile")
        .andExpect(jsonPath("$.fullName").value("Alice Private"))
        .andExpect(jsonPath("$.headline").value("Alice secret headline"));
    api.get(alice.accessToken(), "/api/v1/me/preferences")
        .andExpect(jsonPath("$.desiredRoles").isEmpty());
    assertThat(
            jdbc.queryForObject(
                "select full_name from candidate_profiles where user_id = ?",
                String.class,
                alice.userId()))
        .isEqualTo("Alice Private");
  }

  @Test
  void userIdSuppliedByClientIsIgnored() throws Exception {
    Session alice = api.register(TestApi.uniqueEmail(), "Alice");
    Session bob = api.register(TestApi.uniqueEmail(), "Bob");

    api.get(bob.accessToken(), "/api/v1/me/profile?userId=" + alice.userId())
        .andExpect(jsonPath("$.fullName").value("Bob"));
    api.put(
            bob.accessToken(),
            "/api/v1/me/profile?userId=" + alice.userId(),
            json("{'fullName':'Hijacked','userId':'%s'}", alice.userId()))
        .andExpect(status().isOk());
    api.get(alice.accessToken(), "/api/v1/me/profile")
        .andExpect(jsonPath("$.fullName").value("Alice"));
  }

  @Test
  void noRouteExposesProfilesById() throws Exception {
    Session alice = api.register();
    Session bob = api.register();
    api.get(bob.accessToken(), "/api/v1/profiles/" + alice.userId())
        .andExpect(status().isNotFound());
    api.get(bob.accessToken(), "/api/v1/users/" + alice.userId() + "/profile")
        .andExpect(status().isNotFound());
  }

  @Test
  void forgedTokensForAnotherUserAreRejected() throws Exception {
    Session alice = api.register();
    Session bob = api.register();
    Instant now = Instant.now();

    String tampered = TestTokens.withTamperedSubject(bob.accessToken(), alice.userId());
    String wrongKey =
        TestTokens.sign(
            "attacker-secret-0123456789abcdef0123456789abcdef",
            alice.userId(),
            now,
            now.plusSeconds(600),
            0);
    for (String forged : new String[] {tampered, wrongKey, TestTokens.unsigned(alice.userId())}) {
      api.get(forged, "/api/v1/me/profile").andExpect(status().isUnauthorized());
      api.put(forged, "/api/v1/me/profile", json("{'fullName':'Hijacked'}"))
          .andExpect(status().isUnauthorized());
    }
    api.get(alice.accessToken(), "/api/v1/me/profile")
        .andExpect(jsonPath("$.fullName").value("Test User"));
  }

  @Test
  void candidatesCannotAccessAdminRoutes() throws Exception {
    Session s = api.register();
    api.get(s.accessToken(), "/api/v1/admin/users")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }
}
