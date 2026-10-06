package com.runiverse.running_service.infrastructure.security.jwt.validator;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public record AudienceValidator(
        String audience
) implements OAuth2TokenValidator<Jwt> {

    private static final String DESCRIPTION = "토큰의 aud 클레임이 이 API를 가리키지 않습니다";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, DESCRIPTION, null)
        );
    }
}
