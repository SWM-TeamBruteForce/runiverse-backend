package com.runiverse.running_service.unit_test.infrastructure.security.jwt.validator;

import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;
import com.runiverse.running_service.infrastructure.security.jwt.validator.BlockedTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BlockedTokenValidatorTest {

    private static final String JTI = UUID.randomUUID().toString();
    @Mock
    private CheckBlockedAccessTokenPort checkBlockedAccessTokenPort;
    private BlockedTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BlockedTokenValidator(checkBlockedAccessTokenPort);
    }

    @Test
    @DisplayName("블랙리스트에 등록된 jti면 거부한다")
    void validateRejectsBlockedToken() {
        // given
        when(checkBlockedAccessTokenPort.isBlocked(JTI)).thenReturn(true);
        // when
        OAuth2TokenValidatorResult result = validator.validate(jwtWithJti(JTI));
        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anySatisfy(error -> assertThat(error.getErrorCode())
                        .isEqualTo(BlockedTokenValidator.ERROR_CODE));
    }

    @Test
    @DisplayName("블랙리스트에 없는 jti면 통과한다")
    void validateAcceptsNormalToken() {
        // given
        when(checkBlockedAccessTokenPort.isBlocked(JTI)).thenReturn(false);
        // when & then
        assertThat(validator.validate(jwtWithJti(JTI)).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("jti가 없는 토큰은 조회 없이 통과한다")
    void validateSkipsLookupWhenJtiAbsent() {
        // when
        OAuth2TokenValidatorResult result = validator.validate(jwtWithJti(null));
        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(checkBlockedAccessTokenPort);
    }

    private Jwt jwtWithJti(String jti) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user");
        if (jti != null) {
            builder.jti(jti);
        }
        return builder.build();
    }
}
