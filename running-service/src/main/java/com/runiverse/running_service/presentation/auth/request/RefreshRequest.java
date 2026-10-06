package com.runiverse.running_service.presentation.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {

}
