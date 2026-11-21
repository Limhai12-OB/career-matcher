package com.careermatcher.auth;

import com.careermatcher.user.Role;
import com.careermatcher.user.UserAccount;
import com.careermatcher.user.UserRepository;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD if no account with that e-mail
 * exists. Never modifies or promotes an existing account, and never logs the credentials. Change
 * the password after first sign-in and remove ADMIN_PASSWORD from the environment.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
  private static final Pattern POLICY = Pattern.compile("^(?=.*\\p{L})(?=.*\\p{Nd}).{12,128}$", Pattern.DOTALL);

  private final String email;
  private final String password;
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AdminBootstrap(
      @Value("${app.admin.email:}") String email,
      @Value("${app.admin.password:}") String password,
      UserRepository users,
      PasswordEncoder encoder) {
    this.email = email;
    this.password = password;
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank()) {
      return;
    }
    if (!POLICY.matcher(password).matches()) {
      log.error("ADMIN_PASSWORD does not meet the policy (12-128 chars, letter and digit); admin not created");
      return;
    }
    String normalized = UserAccount.normalizeEmail(email);
    users.findByEmail(normalized).ifPresentOrElse(
        existing -> {
          if (existing.getRole() != Role.ADMIN) {
            log.warn("ADMIN_EMAIL belongs to an existing non-admin account; it was not promoted");
          }
        },
        () -> {
          users.save(new UserAccount(normalized, encoder.encode(password), Role.ADMIN));
          log.info("Bootstrap administrator account created");
        });
  }
}
