package com.runiverse.running_service.infrastructure.oauth.apple;

import ch.qos.logback.classic.Level;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.OauthProviderUnavailableException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class AppleOauthClientTest {

    private static final String CLIENT_ID = "com.runiverse.app";
    private static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";

    // 애플 sub는 '숫자.hex.숫자' 형태의 문자열이다
    private static final String PROVIDER_ID = "001234.abcdef1234567890abcdef1234567890.1234";
    private static final String EMAIL = "runner@privaterelay.appleid.com";
    private static final String RAW_NONCE = "apple-raw-nonce";
    // SHA-256("apple-raw-nonce")의 소문자 hex — 앱이 애플에 넘기는 값이다
    private static final String HASHED_NONCE = "97931806e6981baa84c81464dc479f9a04f7c03181e65926fe74427d6862bde2";

    private static RSAKey appleKey;
    private static RSAKey otherKey;

    private MockRestServiceServer mockServer;
    private AppleOauthClient client;
    private LogCapture log;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        appleKey = new RSAKeyGenerator(2048).keyID("apple-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("apple-key").generate();
    }

    // 실제 네트워크 없이 애플 공개키 응답을 흉내내기 위해 RestTemplate에 MockRestServiceServer를 바인딩한다
    @BeforeEach
    void setUp() {
        RestTemplateBuilder builder = new RestTemplateBuilder(
                restTemplate -> mockServer = MockRestServiceServer.bindTo(restTemplate).build());
        client = new AppleOauthClient(new AppleOauthProperties(CLIENT_ID, JWK_SET_URI), builder);
        log = LogCapture.of(AppleOauthClient.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private void respondWithAppleKeys() {
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(JWK_SET_URI))
                .andRespond(withSuccess(new JWKSet(appleKey.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    }

    private static String idToken(Consumer<JWTClaimsSet.Builder> customizer) {
        return idToken(appleKey, customizer);
    }

    private static String idToken(RSAKey signingKey, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject(PROVIDER_ID)
                .claim("nonce", HASHED_NONCE)
                .claim("email", EMAIL)
                .claim("email_verified", "true")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingKey.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims.build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    @Test
    @DisplayName("애플이 서명한 ID 토큰의 nonce가 원본의 SHA-256과 일치하면 애플 프로필을 반환한다")
    void loadReturnsProfile() {
        // given
        respondWithAppleKeys();

        // when
        OauthProfile profile = client.load(idToken(claims -> {
        }), RAW_NONCE);

        // then
        assertThat(profile).isEqualTo(new OauthProfile(Provider.APPLE, PROVIDER_ID, EMAIL));
    }

    @Test
    @DisplayName("email_verified가 문자열이 아니라 불리언 true로 와도 받는다")
    void loadAcceptsBooleanEmailVerified() {
        // given -> 애플은 같은 클레임을 문자열 "true"로도, 불리언 true로도 발급한다
        respondWithAppleKeys();

        // when
        OauthProfile profile = client.load(idToken(claims -> claims.claim("email_verified", true)), RAW_NONCE);

        // then
        assertThat(profile.providerId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    @DisplayName("보낸 nonce의 해시가 토큰의 nonce와 다르면 OauthLoginFailedException을 던지고 WARN을 남긴다")
    void loadRejectsMismatchedNonce() {
        // given
        respondWithAppleKeys();
        String token = idToken(claims -> {
        });

        // when & then
        assertThatThrownBy(() -> client.load(token, "other-nonce"))
                .isInstanceOf(OauthLoginFailedException.class);
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 애플 로그인 실패: nonce 불일치");
    }

    @Test
    @DisplayName("원본 nonce를 해시하지 않고 그대로 넣은 토큰이면 OauthLoginFailedException을 던진다")
    void loadRejectsUnhashedNonceClaim() {
        // given -> 앱이 해시 없이 원본을 애플에 넘긴 경우다
        respondWithAppleKeys();
        String token = idToken(claims -> claims.claim("nonce", RAW_NONCE));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    @Test
    @DisplayName("토큰에 nonce 클레임이 없으면 OauthLoginFailedException을 던진다")
    void loadRejectsMissingNonceClaim() {
        // given
        respondWithAppleKeys();
        String token = idToken(claims -> claims.claim("nonce", null));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    @Test
    @DisplayName("다른 앱용으로 발급된 토큰(aud 불일치)이면 OauthLoginFailedException을 던지고 원인을 WARN으로 남긴다")
    void loadRejectsOtherAudience() {
        // given
        respondWithAppleKeys();
        String token = idToken(claims -> claims.audience("com.other.app"));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 애플 로그인 실패: ID 토큰 규칙 위반 - reasons=[토큰의 aud 클레임이 이 API를 가리키지 않습니다]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"appleid.apple.com", "https://accounts.google.com"})
    @DisplayName("애플 발급자 표기와 정확히 같지 않으면 OauthLoginFailedException을 던진다")
    void loadRejectsOtherIssuer(String issuer) {
        // given -> 구글과 달리 애플은 iss를 한 표기로만 발급한다
        respondWithAppleKeys();
        String token = idToken(claims -> claims.issuer(issuer));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    @Test
    @DisplayName("만료된 토큰이면 OauthLoginFailedException을 던진다")
    void loadRejectsExpiredToken() {
        // given -> 기본 허용 오차(60초)를 넘겨 만료시킨다
        respondWithAppleKeys();
        String token = idToken(claims -> claims.expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(5)))));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    @Test
    @DisplayName("exp가 없는 토큰이면 OauthLoginFailedException을 던진다")
    void loadRejectsTokenWithoutExpiry() {
        // given -> 기본 검증기는 exp가 없으면 만료 검사를 건너뛴다
        respondWithAppleKeys();
        String token = idToken(claims -> claims.expirationTime(null));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
        assertThat(log.messages(Level.WARN)).singleElement().asString()
                .startsWith("[인증] 애플 로그인 실패: ID 토큰 규칙 위반");
    }

    // 해석·서명 검증 실패는 원인 예외의 종류만 남긴다 — 토큰·이메일이 로그에 섞이지 않는다
    private void assertLoggedCause(String causeName) {
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 애플 로그인 실패: ID 토큰 해석·서명 검증 실패 - cause=" + causeName);
        assertThat(log.events(Level.WARN).getFirst().getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("애플 키로 서명하지 않은 토큰이면 OauthLoginFailedException을 던지고 원인 예외 종류만 남긴다")
    void loadRejectsForgedSignature() {
        // given -> kid는 같지만 다른 키로 서명했다
        respondWithAppleKeys();
        String token = idToken(otherKey, claims -> {
        });

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("BadJWSException");
    }

    @Test
    @DisplayName("JWT 형식이 아니면 OauthLoginFailedException을 던지고 원인 예외 종류만 남긴다")
    void loadRejectsMalformedToken() {
        // when & then
        assertThatThrownBy(() -> client.load("not-a-jwt", RAW_NONCE))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("ParseException");
    }

    @Test
    @DisplayName("email 스코프에 동의하지 않아 이메일이 없으면 OauthEmailNotProvidedException을 던진다")
    void loadRejectsMissingEmail() {
        // given
        respondWithAppleKeys();
        String token = idToken(claims -> claims.claim("email", null).claim("email_verified", null));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthEmailNotProvidedException.class);
    }

    @Test
    @DisplayName("애플이 소유를 확인하지 않은 이메일이면 OauthEmailNotProvidedException을 던진다")
    void loadRejectsUnverifiedEmail() {
        // given
        respondWithAppleKeys();
        String token = idToken(claims -> claims.claim("email_verified", "false"));

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthEmailNotProvidedException.class);
    }

    @Test
    @DisplayName("애플 공개키를 받아 오지 못하면 OauthProviderUnavailableException을 던지고 ERROR를 예외와 함께 남긴다")
    void loadFailsWhenJwkSetUnavailable() {
        // given
        mockServer.expect(requestTo(JWK_SET_URI)).andRespond(withServerError());
        String token = idToken(claims -> {
        });

        // when & then
        assertThatThrownBy(() -> client.load(token, RAW_NONCE))
                .isInstanceOf(OauthProviderUnavailableException.class);
        assertThat(log.events(Level.ERROR))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo("[인증] 애플 로그인 실패: 애플 공개키 조회 오류");
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
        assertThat(log.messages(Level.WARN)).isEmpty();
    }
}
