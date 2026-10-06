package com.runiverse.running_service.presentation.auth.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "인가 코드는 필수입니다.")
        String authorizationCode,
        @NotBlank(message = "코드 검증값은 필수입니다.")
        String codeVerifier
) {

}
