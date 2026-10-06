package com.runiverse.running_service.infrastructure.oauth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean isEmailValid,
            @JsonProperty("is_email_verified") Boolean isEmailVerified
    ) {

    }
}
