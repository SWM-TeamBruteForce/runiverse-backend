package com.runiverse.running_service.presentation.auth.request;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank(message = "ID 토큰은 필수입니다.")
        String idToken,
        @NotBlank(message = "nonce는 필수입니다.")
        String nonce
) {

}
