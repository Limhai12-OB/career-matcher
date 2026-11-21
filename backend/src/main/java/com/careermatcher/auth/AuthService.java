package com.careermatcher.auth;

import com.careermatcher.auth.RefreshTokenService.IssuedRefreshToken;
import com.careermatcher.auth.dto.AuthResponse;
import com.careermatcher.auth.dto.LoginRequest;
import com.careermatcher.auth.dto.RegisterRequest;
import com.careermatcher.auth.dto.UserSummary;
import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.ratelimit.RateLimitProperties;
import com.careermatcher.common.ratelimit.RateLimiter;
import com.careermatcher.common.security.AccessTokenIssuer;
import com.careermatcher.common.security.AccessTokenIssuer.IssuedAccessToken;
import com.careermatcher.profile.ProfileService;
import com.careermatcher.user.Role;
import com.careermatcher.user.UserAccount;
import com.careermatcher.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  /** Result for controllers: response body plus the refresh token to place in a cookie. */
  public record AuthResult(AuthResponse body, String refreshToken, Instant refreshExpiresAt) {}

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokens;
  private final AccessTokenIssuer accessTokens;
  private final ProfileService profiles;
  private final RateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final Clock clock;
  /** Hash of a random value, used to equalize login timing for unknown e-mails. */
  private final String timingDummyHash;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokens,
      AccessTokenIssuer accessTokens,
      ProfileService profiles,
      RateLimiter rateLimiter,
      RateLimitProperties limits,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.profiles = profiles;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.clock = clock;
    this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthResult register(RegisterRequest req, String clientIp) {
    rateLimiter.consume("register-ip", clientIp, limits.registerPerIp());
    String email = UserAccount.normalizeEmail(req.email());
    if (users.existsByEmail(email)) {
      throw emailTaken();
    }
    UserAccount user = new UserAccount(email, passwordEncoder.encode(req.password()), Role.CANDIDATE);
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw emailTaken(); // concurrent registration with the same e-mail
    }
    profiles.createInitialProfile(user.getId(), req.fullName());
    users.recordLogin(user.getId(), clock.instant());
    return issueSession(user, false);
  }

  @Transactional
  public AuthResult login(LoginRequest req, String clientIp) {
    String email = UserAccount.normalizeEmail(req.email());
    rateLimiter.consume("login-ip", clientIp, limits.loginPerIp());
    rateLimiter.consume("login-email", email, limits.loginPerEmail());

    UserAccount user = users.findByEmail(email).orElse(null);
    if (user == null) {
      passwordEncoder.matches(req.password(), timingDummyHash);
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }
    if (!user.isActive()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account is disabled.");
    }
    rateLimiter.reset("login-email", email);
    users.recordLogin(user.getId(), clock.instant());
    return issueSession(user, profiles.isOnboardingCompleted(user.getId()));
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult refresh(String presentedRefreshToken, String clientIp) {
    rateLimiter.consume("refresh-ip", clientIp, limits.refreshPerIp());
    IssuedRefreshToken rotated = refreshTokens.rotate(presentedRefreshToken);
    UserAccount user =
        users
            .findById(rotated.userId())
            .filter(UserAccount::isActive)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN",
                        "Session expired. Please sign in again."));
    IssuedAccessToken access =
        accessTokens.issue(user.getId(), user.getRole(), user.getTokenVersion());
    return new AuthResult(
        toResponse(access, user, profiles.isOnboardingCompleted(user.getId())),
        rotated.value(),
        rotated.expiresAt());
  }

  @Transactional
  public void logout(String presentedRefreshToken) {
    refreshTokens.revokeFamilyOf(presentedRefreshToken);
  }

  @Transactional(readOnly = true)
  public UserSummary currentUser(UUID userId) {
    UserAccount user =
        users
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Not signed in."));
    return summary(user, profiles.isOnboardingCompleted(userId));
  }

  /** Starts a new session (new refresh-token family) for the user. */
  AuthResult issueSession(UserAccount user, boolean onboardingCompleted) {
    IssuedAccessToken access =
        accessTokens.issue(user.getId(), user.getRole(), user.getTokenVersion());
    IssuedRefreshToken refresh = refreshTokens.issueNewFamily(user.getId());
    return new AuthResult(
        toResponse(access, user, onboardingCompleted), refresh.value(), refresh.expiresAt());
  }

  private static AuthResponse toResponse(
      IssuedAccessToken access, UserAccount user, boolean onboardingCompleted) {
    return new AuthResponse(
        access.value(), "Bearer", access.expiresInSeconds(), summary(user, onboardingCompleted));
  }

  static UserSummary summary(UserAccount user, boolean onboardingCompleted) {
    return new UserSummary(
        user.getId(), user.getEmail(), user.getRole(), onboardingCompleted, user.getCreatedAt());
  }

  private static ApiException invalidCredentials() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
  }

  private static ApiException emailTaken() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "EMAIL_ALREADY_REGISTERED",
        "An account with this email already exists.");
  }
}
