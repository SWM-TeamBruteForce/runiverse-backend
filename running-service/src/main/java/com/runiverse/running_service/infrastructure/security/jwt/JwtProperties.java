package com.runiverse.running_service.infrastructure.security.jwt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @Valid TokenSpec accessToken,
        @NotNull @Valid TokenSpec refreshToken
) {

    public record TokenSpec(@NotBlank String secret, @NotNull Duration ttl) {

    }
}
