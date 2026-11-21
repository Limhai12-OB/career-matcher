package com.careermatcher.auth;

import com.careermatcher.auth.AuthService.AuthResult;
import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.error.FieldValidationException;
import com.careermatcher.common.ratelimit.RateLimitProperties;
import com.careermatcher.common.ratelimit.RateLimiter;
import com.careermatcher.cv.CvService;
import com.careermatcher.profile.ProfileService;
import com.careermatcher.user.UserAccount;
import com.careermatcher.user.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sensitive self-service account operations; each requires the current password. */
@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokens;
  private final AuthService authService;
  private final ProfileService profiles;
  private final RateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final CvService cvService;

  public AccountService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokens,
      AuthService authService,
      ProfileService profiles,
      RateLimiter rateLimiter,
      RateLimitProperties limits,
      CvService cvService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokens = refreshTokens;
    this.authService = authService;
    this.profiles = profiles;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.cvService = cvService;
  }

  /**
   * Changes the password, revokes every session and access token of the user, and returns a fresh
   * session for the caller.
   */
  @Transactional
  public AuthResult changePassword(UUID userId, String currentPassword, String newPassword) {
    rateLimiter.consume("account-action", userId.toString(), limits.sensitiveAccountActionPerUser());
    UserAccount user = load(userId);
    verifyPassword(user, currentPassword, "currentPassword");
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new FieldValidationException("newPassword", "New password must differ from the current one");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    users.saveAndFlush(user);
    refreshTokens.revokeAllForUser(userId);
    return authService.issueSession(user, profiles.isOnboardingCompleted(userId));
  }

  /**
   * Permanently deletes the account. Profile, preferences, sessions, CV records and skills cascade
   * in the database; stored CV files are deleted after commit.
   */
  @Transactional
  public void deleteAccount(UUID userId, String password) {
    rateLimiter.consume("account-action", userId.toString(), limits.sensitiveAccountActionPerUser());
    UserAccount user = load(userId);
    verifyPassword(user, password, "password");
    // Stored CV files are not covered by database cascades; remove them once this commits.
    cvService.scheduleDeletionOfAllFiles(userId);
    users.delete(user);
    users.flush();
    log.info("Account deleted id={}", userId);
  }

  private UserAccount load(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Not signed in."));
  }

  private void verifyPassword(UserAccount user, String raw, String field) {
    if (!passwordEncoder.matches(raw, user.getPasswordHash())) {
      throw new FieldValidationException(field, "Password is incorrect");
    }
  }
}
