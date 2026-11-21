package com.careermatcher.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
    @NotBlank(message = "Password is required") @Size(max = 128) String password) {

  @Override
  public String toString() {
    return "DeleteAccountRequest[***]";
  }
}
