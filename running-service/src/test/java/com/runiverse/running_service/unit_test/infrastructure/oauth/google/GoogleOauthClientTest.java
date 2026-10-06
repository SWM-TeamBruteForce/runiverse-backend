package com.runiverse.running_service.infrastructure.oauth.google;

import ch.qos.logback.classic.Level;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
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

public class GoogleOauthClientTest {

    private static final String CLIENT_ID = "google-web-client-id.apps.googleusercontent.com";
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    // 구글 sub는 Long 범위를 넘길 수 있는 21자리라 문자열 그대로 다룬다
    private static final String PROVIDER_ID = "107812345678901234567";
    private static final String EMAIL = "google@example.com";

    private static RSAKey googleKey;
    private static RSAKey otherKey;

    private MockRestServiceServer mockServer;
    private GoogleOauthClient client;
    private LogCapture log;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        googleKey = new RSAKeyGenerator(2048).keyID("google-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("google-key").generate();
    }

    // 실제 네트워크 없이 구글 공개키 응답을 흉내내기 위해 RestTemplate에 MockRestServiceServer를 바인딩한다
    @BeforeEach
    void setUp() {
        RestTemplateBuilder builder = new RestTemplateBuilder(
                restTemplate -> mockServer = MockRestServiceServer.bindTo(restTemplate).build());
        client = new GoogleOauthClient(new GoogleOauthProperties(CLIENT_ID, JWK_SET_URI), builder);
        log = LogCapture.of(GoogleOauthClient.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private void respondWithGoogleKeys() {
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(JWK_SET_URI))
                .andRespond(withSuccess(new JWKSet(googleKey.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    }

    private static String idToken(Consumer<JWTClaimsSet.Builder> customizer) {
        return idToken(googleKey, customizer);
    }

    private static String idToken(RSAKey signingKey, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject(PROVIDER_ID)
                .claim("email", EMAIL)
                .claim("email_verified", true)
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
    @DisplayName("구글이 서명한 ID 토큰을 검증해 구글 프로필을 반환한다")
    void loadReturnsProfile() {
        // given
        respondWithGoogleKeys();

        // when
        OauthProfile profile = client.load(idToken(claims -> {
        }));

        // then
        assertThat(profile).isEqualTo(new OauthProfile(Provider.GOOGLE, PROVIDER_ID, EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts.google.com", "https://accounts.google.com"})
    @DisplayName("구글이 쓰는 iss 두 표기를 모두 받는다")
    void loadAcceptsBothGoogleIssuers(String issuer) {
        // given
        respondWithGoogleKeys();

        // when
        OauthProfile profile = client.load(idToken(claims -> claims.issuer(issuer)));

        // then
        assertThat(profile.providerId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    @DisplayName("다른 앱용으로 발급된 토큰(aud 불일치)이면 OauthLoginFailedException을 던지고 원인을 WARN으로 남긴다")
    void loadRejectsOtherAudience() {
        // given
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.audience("other-app-client-id"));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthLoginFailedException.class);
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 구글 로그인 실패: ID 토큰 규칙 위반 - reasons=[토큰의 aud 클레임이 이 API를 가리키지 않습니다]");
    }

    @Test
    @DisplayName("구글이 아닌 발급자면 OauthLoginFailedException을 던진다")
    void loadRejectsOtherIssuer() {
        // given
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.issuer("https://evil.example.com"));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    @Test
    @DisplayName("만료된 토큰이면 OauthLoginFailedException을 던진다")
    void loadRejectsExpiredToken() {
        // given -> 기본 허용 오차(60초)를 넘겨 만료시킨다
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(5)))));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthLoginFailedException.class);
    }

    // 해석·서명 검증 실패는 원인 예외의 종류만 남긴다 — 토큰·이메일이 로그에 섞이지 않는다
    private void assertLoggedCause(String causeName) {
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 구글 로그인 실패: ID 토큰 해석·서명 검증 실패 - cause=" + causeName);
        assertThat(log.events(Level.WARN).getFirst().getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("구글 키로 서명하지 않은 토큰이면 OauthLoginFailedException을 던지고 원인 예외 종류만 남긴다")
    void loadRejectsForgedSignature() {
        // given -> kid는 같지만 다른 키로 서명했다
        respondWithGoogleKeys();
        String token = idToken(otherKey, claims -> {
        });

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("BadJWSException");
    }

    @Test
    @DisplayName("JWT 형식이 아니면 OauthLoginFailedException을 던지고 원인 예외 종류만 남긴다")
    void loadRejectsMalformedToken() {
        // when & then
        assertThatThrownBy(() -> client.load("not-a-jwt"))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("ParseException");
    }

    @Test
    @DisplayName("페이로드가 JSON 객체가 아니면 OauthLoginFailedException을 던지고 원인 예외 종류만 남긴다")
    void loadRejectsNonJsonPayload() throws JOSEException {
        // given -> 구글 키로 서명했지만 페이로드가 JSON 객체가 아니다
        respondWithGoogleKeys();
        JWSObject jws = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(googleKey.getKeyID()).build(),
                new Payload("not-json " + EMAIL));
        jws.sign(new RSASSASigner(googleKey));

        // when & then
        assertThatThrownBy(() -> client.load(jws.serialize()))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("BadJWTException");
    }

    @Test
    @DisplayName("서명 없는 토큰(alg=none)이면 OauthLoginFailedException을 던지고 예외 종류만 남긴다")
    void loadRejectsUnsignedToken() {
        // given
        String token = new PlainJWT(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject(PROVIDER_ID)
                .claim("email", EMAIL)
                .build()).serialize();

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthLoginFailedException.class);
        assertLoggedCause("BadJwtException");
    }

    @Test
    @DisplayName("email 스코프에 동의하지 않아 이메일이 없으면 OauthEmailNotProvidedException을 던진다")
    void loadRejectsMissingEmail() {
        // given
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.claim("email", null).claim("email_verified", null));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthEmailNotProvidedException.class);
    }

    @Test
    @DisplayName("구글이 소유를 확인하지 않은 이메일이면 OauthEmailNotProvidedException을 던진다")
    void loadRejectsUnverifiedEmail() {
        // given
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.claim("email_verified", false));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthEmailNotProvidedException.class);
    }

    @Test
    @DisplayName("이메일은 있는데 인증 여부가 오지 않으면 OauthEmailNotProvidedException을 던진다")
    void loadRejectsEmailWithoutVerifiedClaim() {
        // given
        respondWithGoogleKeys();
        String token = idToken(claims -> claims.claim("email_verified", null));

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthEmailNotProvidedException.class);
    }

    @Test
    @DisplayName("구글 공개키를 받아 오지 못하면 OauthProviderUnavailableException을 던지고 ERROR를 예외와 함께 남긴다")
    void loadFailsWhenJwkSetUnavailable() {
        // given
        mockServer.expect(requestTo(JWK_SET_URI)).andRespond(withServerError());
        String token = idToken(claims -> {
        });

        // when & then
        assertThatThrownBy(() -> client.load(token))
                .isInstanceOf(OauthProviderUnavailableException.class);
        assertThat(log.events(Level.ERROR))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo("[인증] 구글 로그인 실패: 구글 공개키 조회 오류");
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
        assertThat(log.messages(Level.WARN)).isEmpty();
    }
}
