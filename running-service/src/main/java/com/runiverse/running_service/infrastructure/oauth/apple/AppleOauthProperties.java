package com.runiverse.running_service.infrastructure.oauth.apple;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oauth.apple")
@Validated
public record AppleOauthProperties(
        @NotBlank String clientId,      // ID 토큰의 aud — iOS 앱의 번들 ID
        @NotBlank String jwkSetUri
) {

}
