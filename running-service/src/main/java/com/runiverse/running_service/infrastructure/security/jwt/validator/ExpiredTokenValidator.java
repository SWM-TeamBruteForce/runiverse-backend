package com.runiverse.running_service.infrastructure.security.jwt.validator;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

public class ExpiredTokenValidator implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE = "token_expired";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && Instant.now().minus(CLOCK_SKEW).isAfter(expiresAt)) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(ERROR_CODE, "액세스 토큰이 만료되었습니다", null)
            );
        }
        return OAuth2TokenValidatorResult.success();
    }
}
