package com.runiverse.running_service.infrastructure.oauth.kakao;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oauth.kakao")
@Validated
public record KakaoOauthProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String unlinkAdminKey,
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri,
        @NotBlank String unlinkUri
) {

}
