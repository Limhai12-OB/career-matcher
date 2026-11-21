package com.careermatcher.auth;

import com.careermatcher.auth.AuthService.AuthResult;
import com.careermatcher.auth.dto.AuthResponse;
import com.careermatcher.auth.dto.LoginRequest;
import com.careermatcher.auth.dto.RegisterRequest;
import com.careermatcher.auth.dto.UserSummary;
import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.common.security.ClientIp;
import com.careermatcher.common.security.OriginVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookies cookies;
  private final OriginVerifier originVerifier;

  public AuthController(
      AuthService authService, RefreshCookies cookies, OriginVerifier originVerifier) {
    this.authService = authService;
    this.cookies = cookies;
    this.originVerifier = originVerifier;
  }

  @Operation(summary = "Register a candidate account and start a session")
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    return withSession(HttpStatus.CREATED, authService.register(request, ClientIp.of(http)));
  }

  @Operation(summary = "Sign in with email and password")
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return withSession(HttpStatus.OK, authService.login(request, ClientIp.of(http)));
  }

  @Operation(
      summary = "Rotate the refresh-token cookie and obtain a new access token",
      description = "Uses the HttpOnly refresh cookie; no request body.")
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      HttpServletRequest http, HttpServletResponse response) {
    originVerifier.verify(http);
    String presented = cookies.read(http).orElse(null);
    if (presented == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
    }
    try {
      return withSession(HttpStatus.OK, authService.refresh(presented, ClientIp.of(http)));
    } catch (ApiException ex) {
      if ("INVALID_REFRESH_TOKEN".equals(ex.getCode())) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
      }
      throw ex;
    }
  }

  @Operation(summary = "Sign out: revoke the current session and clear the cookie")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest http) {
    originVerifier.verify(http);
    cookies.read(http).ifPresent(authService::logout);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  @Operation(summary = "Get the signed-in user")
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/me")
  public UserSummary me(@AuthenticationPrincipal AuthenticatedUser user) {
    return authService.currentUser(user.id());
  }

  private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE,
            cookies.create(result.refreshToken(), result.refreshExpiresAt()).toString())
        .body(result.body());
  }
}
