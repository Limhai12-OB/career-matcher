package com.careermatcher.auth;

import com.careermatcher.auth.AuthService.AuthResult;
import com.careermatcher.auth.dto.AuthResponse;
import com.careermatcher.auth.dto.ChangePasswordRequest;
import com.careermatcher.auth.dto.DeleteAccountRequest;
import com.careermatcher.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

  private final AccountService accountService;
  private final RefreshCookies cookies;

  public AccountController(AccountService accountService, RefreshCookies cookies) {
    this.accountService = accountService;
    this.cookies = cookies;
  }

  @Operation(
      summary = "Change my password",
      description = "Signs out all other sessions and invalidates existing access tokens.")
  @PostMapping("/password")
  public ResponseEntity<AuthResponse> changePassword(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody ChangePasswordRequest request) {
    AuthResult result =
        accountService.changePassword(user.id(), request.currentPassword(), request.newPassword());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE,
            cookies.create(result.refreshToken(), result.refreshExpiresAt()).toString())
        .body(result.body());
  }

  @Operation(summary = "Permanently delete my account and all my data")
  @DeleteMapping
  public ResponseEntity<Void> deleteAccount(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody DeleteAccountRequest request) {
    accountService.deleteAccount(user.id(), request.password());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }
}
