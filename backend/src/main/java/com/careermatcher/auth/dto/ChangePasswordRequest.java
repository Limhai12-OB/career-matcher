package com.careermatcher.auth.dto;

import com.careermatcher.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required") @Size(max = 128) String currentPassword,
    @StrongPassword String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[***]";
  }
}
