package org.juns.marketboardbackend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordMatch
public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String passwordConfirm,
        @NotBlank @Size(min = 2, max = 50) String username,
        @AssertTrue(message = "약관에 동의해야 합니다") boolean termsAgreed) {
}
