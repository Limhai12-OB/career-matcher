package com.careermatcher.support;

import com.careermatcher.user.Role;
import com.careermatcher.user.UserAccount;
import com.careermatcher.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates admin accounts directly (there is no public way to become an admin). */
@TestComponent
public class AdminSupport {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TestApi api;

  public AdminSupport(UserRepository users, PasswordEncoder encoder, TestApi api) {
    this.users = users;
    this.encoder = encoder;
    this.api = api;
  }

  public TestApi.Session admin() throws Exception {
    String email = "admin-" + TestApi.uniqueEmail();
    users.save(new UserAccount(email, encoder.encode(TestApi.PASSWORD), Role.ADMIN));
    return api.login(email, TestApi.PASSWORD);
  }
}
