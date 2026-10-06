package com.runiverse.running_service.unit_test.infrastructure.security.jwt.validator;

import com.runiverse.running_service.infrastructure.security.jwt.validator.AudienceValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AudienceValidatorTest {

    private static final String AUDIENCE = "runiverse-api";
    private final AudienceValidator validator = new AudienceValidator(AUDIENCE);

    @Test
    @DisplayName("aud에 우리 API가 포함되면 통과한다")
    void validateAcceptsMatchingAudience() {
        // given
        Jwt jwt = jwtWithAudience(List.of(AUDIENCE));

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("aud가 다른 서비스를 가리키면 거부한다")
    void validateRejectsOtherAudience() {
        // given
        Jwt jwt = jwtWithAudience(List.of("other-service"));

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anySatisfy(error -> assertThat(error.getDescription()).contains("aud"));
    }

    @Test
    @DisplayName("aud 클레임이 없으면 거부한다")
    void validateRejectMissingAudience() {
        // given
        Jwt jwt = jwtWithAudience(null);

        // when & then
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwtWithAudience(List<String> audiences) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user");
        if (audiences != null) {
            builder.audience(audiences);
        }
        return builder.build();
    }
}
