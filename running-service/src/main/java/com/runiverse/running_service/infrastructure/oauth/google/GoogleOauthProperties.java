package com.runiverse.running_service.infrastructure.oauth.google;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oauth.google")
@Validated
public record GoogleOauthProperties(
        @NotBlank String clientId,      // ID 토큰의 aud — 앱이 serverClientId로 넘기는 웹 클라이언트 ID
        @NotBlank String jwkSetUri
) {

}
