package com.runiverse.running_service.infrastructure.security.jwt.validator;

import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public record BlockedTokenValidator(
        CheckBlockedAccessTokenPort checkBlockedAccessTokenPort
) implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE = "token_blocked";
    private static final String DESCRIPTION = "로그아웃된 액세스 토큰입니다";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String accessTokenId = jwt.getId();
        if (accessTokenId != null && checkBlockedAccessTokenPort.isBlocked(accessTokenId)) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(ERROR_CODE, DESCRIPTION, null)
            );
        }
        return OAuth2TokenValidatorResult.success();
    }
}
