package com.runiverse.running_service.unit_test.infrastructure.config;

import com.runiverse.running_service.infrastructure.oauth.google.GoogleOauthProperties;
import com.runiverse.running_service.infrastructure.oauth.kakao.KakaoOauthProperties;
import com.runiverse.running_service.infrastructure.security.CorsProperties;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import com.runiverse.running_service.infrastructure.websocket.WebSocketProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// .env.example을 복사하고 값을 비워 두면 빈 문자열로 바인딩돼 서버가 그대로 뜬다 —
// 여기서 막지 않으면 로그인·토큰 발급을 부를 때에야 드러난다
@DisplayName("설정 레코드 검증 단위 테스트")
class ConfigurationPropertiesValidationTest {

    private static final JwtProperties.TokenSpec TOKEN =
            new JwtProperties.TokenSpec("secret", Duration.ofMinutes(15));

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("JWT 발급자가 비면 위반이다")
    void jwtRejectsBlankIssuer() {
        // when
        Set<ConstraintViolation<JwtProperties>> violations =
                validator.validate(new JwtProperties("", "runiverse-api", TOKEN, TOKEN));

        // then
        assertThat(paths(violations)).containsExactly("issuer");
    }

    @Test
    @DisplayName("JWT 토큰 서명키가 비면 안쪽까지 검증해 위반이다")
    void jwtRejectsBlankSecretInsideTokenSpec() {
        // given -> @Valid가 없으면 안쪽 레코드의 제약은 검사되지 않는다
        JwtProperties.TokenSpec blankSecret = new JwtProperties.TokenSpec("", Duration.ofMinutes(15));

        // when
        Set<ConstraintViolation<JwtProperties>> violations =
                validator.validate(new JwtProperties("runiverse", "runiverse-api", blankSecret, TOKEN));

        // then
        assertThat(paths(violations)).containsExactly("accessToken.secret");
    }

    @Test
    @DisplayName("CORS 허용 출처가 비면 위반이다")
    void corsRejectsEmptyOrigins() {
        // when
        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(
                new CorsProperties(List.of(), List.of("GET"), List.of("*")));

        // then
        assertThat(paths(violations)).containsExactly("allowedOrigins");
    }

    @Test
    @DisplayName("카카오 client secret이 비면 위반이다")
    void kakaoRejectsBlankClientSecret() {
        // given -> 콘솔에서 client secret을 쓰고 있어 비면 토큰 요청이 거절된다
        KakaoOauthProperties properties = new KakaoOauthProperties(
                "client-id", "", "redirect", "admin-key", "token-uri", "user-info-uri", "unlink-uri");

        // when
        Set<ConstraintViolation<KakaoOauthProperties>> violations = validator.validate(properties);

        // then
        assertThat(paths(violations)).containsExactly("clientSecret");
    }

    @Test
    @DisplayName("구글 client id가 비면 위반이다")
    void googleRejectsBlankClientId() {
        // given -> ID 토큰의 aud로 검증하는 값이라 비면 모든 구글 로그인이 거절된다
        GoogleOauthProperties properties = new GoogleOauthProperties("", "jwk-set-uri");

        // when
        Set<ConstraintViolation<GoogleOauthProperties>> violations = validator.validate(properties);

        // then
        assertThat(paths(violations)).containsExactly("clientId");
    }

    @Test
    @DisplayName("러닝 WebSocket 경로가 비면 위반이다")
    void webSocketRejectsBlankEndpoint() {
        // given -> @NotNull은 빈 문자열을 통과시킨다
        WebSocketProperties properties = new WebSocketProperties(
                Duration.ofMinutes(2), DataSize.ofKilobytes(64), "");

        // when
        Set<ConstraintViolation<WebSocketProperties>> violations = validator.validate(properties);

        // then
        assertThat(paths(violations)).containsExactly("runningEndpoint");
    }

    private static <T> List<String> paths(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .toList();
    }
}
