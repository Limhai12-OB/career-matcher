package com.careermatcher.auth.dto;

import com.careermatcher.common.validation.StrongPassword;
import com.careermatcher.common.validation.TextRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long")
        String email,
    @StrongPassword String password,
    @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
        String fullName) {

  @Override
  public String toString() {
    return "RegisterRequest[email=***, password=***, fullName=***]";
  }
}
