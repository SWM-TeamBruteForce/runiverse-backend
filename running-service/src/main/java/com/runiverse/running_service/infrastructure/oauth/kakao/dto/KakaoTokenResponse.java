package com.runiverse.running_service.infrastructure.oauth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoTokenResponse(
        @JsonProperty("access_token") String accessToken
) {

}
