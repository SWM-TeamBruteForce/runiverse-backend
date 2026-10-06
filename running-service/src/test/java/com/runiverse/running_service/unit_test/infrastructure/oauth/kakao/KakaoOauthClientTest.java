package com.runiverse.running_service.infrastructure.oauth.kakao;


import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.OauthProviderUnavailableException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

public class KakaoOauthClientTest {

    private static final String CLIENT_ID = "kakao-rest-api-key";
    private static final String CLIENT_SECRET = "kakao-client-secret";
    private static final String REDIRECT_URI = "http://localhost:5173";
    private static final String UNLINK_ADMIN_KEY = "unlink-admin-key";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

    private static final String AUTHORIZATION_CODE = "kakao-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";
    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";

    private static final String PROVIDER_ID = "1234567890";
    private static final String EMAIL = "kakao@example.com";

    private static final String TOKEN_RESPONSE =
            """
                    {
                      "token_type": "bearer",
                      "access_token": "kakao-access-token",
                      "expires_in": 21599,
                      "refresh_token": "kakao-refresh-token"
                    }
                    """;

    private static final String USER_RESPONSE =
            """
                    {
                      "id": 1234567890,
                      "connected_at": "2026-07-30T00:00:00Z",
                      "kakao_account": {
                        "has_email": true,
                        "email_needs_agreement": false,
                        "is_email_valid": true,
                        "is_email_verified": true,
                        "email": "kakao@example.com"
                      }
                    }
                    """;

    // 이메일 동의를 받지 못하면 email 필드 자체가 응답에서 빠진다
    private static final String USER_RESPONSE_WITHOUT_EMAIL =
            """
                    {
                      "id": 1234567890,
                      "kakao_account": {
                        "has_email": true,
                        "email_needs_agreement": true
                      }
                    }
                    """;

    // 카카오계정에 등록만 되고 소유가 확인되지 않은 이메일
    private static final String USER_RESPONSE_UNVERIFIED_EMAIL =
            """
                    {
                      "id": 1234567890,
                      "kakao_account": {
                        "is_email_valid": true,
                        "is_email_verified": false,
                        "email": "kakao@example.com"
                      }
                    }
                    """;

    // 다른 카카오계정에 사용돼 만료된 이메일 — 카카오가 마스킹해서 준다
    private static final String USER_RESPONSE_INVALID_EMAIL =
            """
                    {
                      "id": 1234567890,
                      "kakao_account": {
                        "is_email_valid": false,
                        "is_email_verified": true,
                        "email": "ka***@example.com"
                      }
                    }
                    """;

    private static final String USER_RESPONSE_WITHOUT_EMAIL_STATUS =
            """
                    {
                      "id": 1234567890,
                      "kakao_account": {
                        "email": "kakao@example.com"
                      }
                    }
                    """;

    // 동의 항목이 하나도 없으면 kakao_account 자체가 오지 않는다
    private static final String USER_RESPONSE_WITHOUT_ACCOUNT =
            """
                    {
                      "id": 1234567890
                    }
                    """;

    private MockRestServiceServer mockServer;
    private LogCapture log;

    @BeforeEach
    void startLogCapture() {
        log = LogCapture.of(KakaoOauthClient.class);
    }

    @AfterEach
    void stopLogCapture() {
        log.stop();
    }

    // 실제 네트워크 없이 카카오 응답을 흉내내기 위해 빌더에 MockRestServiceServer를 바인딩한다
    private KakaoOauthClient createClient(String clientSecret) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        KakaoOauthProperties properties = new KakaoOauthProperties(
                CLIENT_ID,
                clientSecret,
                REDIRECT_URI,
                UNLINK_ADMIN_KEY,
                TOKEN_URI,
                USER_INFO_URI,
                UNLINK_URI
        );

        return new KakaoOauthClient(builder.build(), properties, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("인가 코드를 교환해 카카오 프로필을 반환한다")
    void exchangeReturnsProfile() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", CLIENT_ID);
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", AUTHORIZATION_CODE);
        expectedForm.add("client_secret", CLIENT_SECRET);
        expectedForm.add("code_verifier", CODE_VERIFIER);

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KAKAO_ACCESS_TOKEN))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        OauthProfile profile = client.load(AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(profile.provider()).isEqualTo(Provider.KAKAO);
        assertThat(profile.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(profile.email()).isEqualTo(EMAIL);

        mockServer.verify();
    }

    @Test
    @DisplayName("client_secret이 없으면 폼에 담지 않는다")
    void exchangeOmitsBlankClientSecret() {
        // given -> 콘솔에서 client_secret을 끈 클라이언트
        KakaoOauthClient client = createClient("");

        // formData는 완전 일치를 검사하므로 이 5개만 있어야 통과한다
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", CLIENT_ID);
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", AUTHORIZATION_CODE);
        expectedForm.add("code_verifier", CODE_VERIFIER);

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        client.load(AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 요청이 실패하면 OauthLoginFailedException을 던진다")
    void exchangeFailsWhenTokenRequestRejected() {
        // given -> 인가 코드 재사용 시 카카오가 KOE320으로 거부한다
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body(
                                """
                                        {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}
                                        """)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 응답에 access_token이 없으면 OauthLoginFailedException을 던진다")
    void exchangeFailsWhenAccessTokenMissing() {
        // given -> 200이지만 본문이 비어 있는 경우를 방어한다
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("사용자 정보 조회가 실패하면 OauthLoginFailedException을 던진다")
    void exchangeFailsWhenUserRequestRejected() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withUnauthorizedRequest());

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("이메일 동의를 받지 못하면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailNotAgreed() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_EMAIL, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("kakao_account 자체가 없어도 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenKakaoAccountMissing() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_ACCOUNT, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("인증되지 않은 이메일이면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailUnverified() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_UNVERIFIED_EMAIL, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("다른 카카오계정에 사용돼 만료된 이메일이면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailInvalid() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_INVALID_EMAIL, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("이메일의 유효·인증 여부가 오지 않으면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailStatusMissing() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_EMAIL_STATUS, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 요청이 4xx로 거부되면 본문 대신 오류 코드만 담아 WARN으로 남긴다")
    void logsTokenRejectionAsWarnWithErrorCode() {
        // given -> 인가 코드 재사용·만료가 대부분이지만 우리 설정 오류일 수도 있어 INFO로 묻지 않는다
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body("""
                                {"error":"invalid_grant","error_description":"authorization code not found for code=%s","error_code":"KOE320"}
                                """.formatted(AUTHORIZATION_CODE))
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        // then -> 오류 설명에 되돌아온 인가 코드는 남기지 않는다
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 카카오 토큰 요청 실패: 카카오 응답 오류 - status=400, errorCode=KOE320");
        assertThat(log.messages(Level.WARN)).noneMatch(message -> message.contains(AUTHORIZATION_CODE));
        assertThat(log.events(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("토큰 요청이 5xx로 실패하면 외부 장애라 ERROR로 남긴다")
    void logsTokenServerErrorAsError() {
        // given -> 게이트웨이 오류 페이지처럼 JSON이 아닌 본문이 올 수 있다
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withServerError().body("<html>bad gateway</html>").contentType(MediaType.TEXT_HTML));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthProviderUnavailableException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 토큰 요청 실패: 카카오 응답 오류 - status=500, errorCode=unknown");
    }

    @Test
    @DisplayName("사용자 조회가 거부되면 사용자 조회 문구로 오류 코드를 남긴다")
    void logsUserRejectionWithErrorCode() {
        // given -> 카카오 사용자 API는 오류 코드를 숫자 code로 준다
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withUnauthorizedRequest()
                        .body("""
                                {"msg":"this access token does not exist","code":-401}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        // then
        assertThat(log.messages(Level.WARN))
                .containsExactly("[인증] 카카오 사용자 조회 실패: 카카오 응답 오류 - status=401, errorCode=-401");
    }

    @Test
    @DisplayName("사용자 조회가 5xx로 실패하면 OauthProviderUnavailableException을 던지고 ERROR로 남긴다")
    void userInfoServerErrorIsProviderUnavailable() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withServerError());

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthProviderUnavailableException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 사용자 조회 실패: 카카오 응답 오류 - status=500, errorCode=unknown");
    }

    @Test
    @DisplayName("사용자 조회가 호출 한도 초과(400 + code -10)면 OauthProviderUnavailableException을 던지고 ERROR로 남긴다")
    void userInfoApiLimitExceededIsProviderUnavailable() {
        // given -> 카카오는 한도 초과를 429가 아니라 400에 code -10으로 준다
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withBadRequest()
                        .body("""
                                {"msg":"API limit has been exceeded.","code":-10}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthProviderUnavailableException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 사용자 조회 실패: 카카오 응답 오류 - status=400, errorCode=-10");
        assertThat(log.messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("토큰 요청이 429로 거부되면 OauthProviderUnavailableException을 던지고 ERROR로 남긴다")
    void tokenTooManyRequestsIsProviderUnavailable() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withTooManyRequests());

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthProviderUnavailableException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 토큰 요청 실패: 카카오 응답 오류 - status=429, errorCode=unknown");
    }

    @Test
    @DisplayName("200인데 access_token이 없으면 카카오 계약이 깨진 것이라 ERROR로 남긴다")
    void logsMissingAccessTokenAsError() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 토큰 요청 실패: access_token 누락");
    }

    @Test
    @DisplayName("200인데 id가 없으면 카카오 계약이 깨진 것이라 ERROR로 남긴다")
    void logsMissingIdAsError() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthLoginFailedException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 사용자 조회 실패: id 누락");
    }

    @Test
    @DisplayName("통신 자체가 끊기면 원인 예외를 담아 ERROR로 남긴다")
    void logsNetworkFailureAsErrorWithCause() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withException(new IOException("connection reset")));

        // when
        assertThatThrownBy(() -> client.load(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthProviderUnavailableException.class);

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[인증] 카카오 로그인 실패: 카카오 통신 오류");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("연동 해제가 거부되면 던지지 않고 본문 없이 status만 [회원] 태그로 남긴다")
    void logsUnlinkRejectionWithoutBody() {
        // given -> 카카오 오류 본문에 어드민 키가 섞여 온다
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(UNLINK_URI))
                .andRespond(withUnauthorizedRequest()
                        .body("{\"msg\":\"wrong admin key: KakaoAK %s\",\"code\":-401}".formatted(UNLINK_ADMIN_KEY))
                        .contentType(MediaType.APPLICATION_JSON));

        // when -> 탈퇴가 커밋된 뒤라 되돌릴 수 없다
        assertThatCode(() -> client.unlink(new ProviderId(PROVIDER_ID))).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.WARN))
                .containsExactly("[회원] 카카오 연동 해제 실패: 카카오 응답 오류 - status=401");
        assertThat(log.messages(Level.WARN)).noneMatch(message -> message.contains(UNLINK_ADMIN_KEY));
    }

    @Test
    @DisplayName("연동 해제 통신이 끊기면 예외 객체 대신 종류만 ERROR로 남긴다")
    void logsUnlinkNetworkFailureWithoutThrowable() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);
        mockServer.expect(requestTo(UNLINK_URI))
                .andRespond(withException(new IOException("connection reset")));

        // when
        assertThatCode(() -> client.unlink(new ProviderId(PROVIDER_ID))).doesNotThrowAnyException();

        // then -> 예외 메시지에 어드민 키가 섞일 수 있어 스택트레이스를 남기지 않는다
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[회원] 카카오 연동 해제 실패: 카카오 통신 오류 - cause=ResourceAccessException");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNull();
    }
}
