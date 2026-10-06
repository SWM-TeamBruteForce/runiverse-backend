package com.runiverse.running_service.presentation.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
        @Size(max = 254, message = "올바른 이메일 형식이 아닙니다.")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email
) {

}
