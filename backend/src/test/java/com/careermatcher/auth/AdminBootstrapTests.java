package com.careermatcher.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.user.Role;
import com.careermatcher.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(
    properties = {"app.admin.email=Boss@Example.com", "app.admin.password=Admin-password-2026"})
class AdminBootstrapTests {

  @Autowired TestApi api;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void bootstrapAdminCanSignInAndManageJobs() throws Exception {
    var admin = api.login("boss@example.com", "Admin-password-2026");
    api.get(admin.accessToken(), "/api/v1/auth/me").andExpect(jsonPath("$.role").value("ADMIN"));
    api.get(admin.accessToken(), "/api/v1/admin/jobs").andExpect(status().isOk());
  }

  @Test
  void neverPromotesOrOverwritesExistingAccountsAndRejectsWeakPasswords() throws Exception {
    var candidate = api.register();
    new AdminBootstrap(candidate.email(), "Another-password-2026", users, encoder).run(null);
    assertThat(users.findByEmail(candidate.email()).orElseThrow().getRole()).isEqualTo(Role.CANDIDATE);
    api.login(candidate.email(), TestApi.PASSWORD); // password unchanged

    new AdminBootstrap("weak@admin.test", "short1", users, encoder).run(null);
    assertThat(users.findByEmail("weak@admin.test")).isEmpty();
  }
}
