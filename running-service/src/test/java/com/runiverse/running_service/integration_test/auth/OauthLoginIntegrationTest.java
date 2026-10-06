package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginHandler;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginResult;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthUserResolver;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.UnsupportedProviderException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.infrastructure.metrics.AuthMetricAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.runiverse.running_service.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("소셜 로그인 통합 테스트")
public class OauthLoginIntegrationTest extends IntegrationTestSupport {

    private static final String AUTH_CODE = "kakao-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";
    private static final String KAKAO_ID = "1234567890";
    private static final String KAKAO_EMAIL = "runner@kakao.com";
    private static final String GOOGLE_ID_TOKEN = "google-id-token";
    private static final String GOOGLE_ID = "107812345678901234567";
    private static final String GOOGLE_EMAIL = "runner@gmail.com";
    private SignUpHandler signUpHandler;
    private OauthLoginHandler oauthLoginHandler;
    private LogCapture handlerLog;
    private SimpleMeterRegistry meterRegistry;
    private LogCapture resolverLog;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        OauthUserResolver oauthUserResolver = new OauthUserResolver(
                userStore,        // LoadUserByProviderPort
                userStore,        // CheckEmailDuplicatePort
                userIdGenerator,  // GenerateUserIdPort
                userStore         // SaveUserPort
        );
        meterRegistry = new SimpleMeterRegistry();
        oauthLoginHandler = new OauthLoginHandler(
                oauthClient,        // LoadKakaoProfilePort
                oauthClient,        // LoadGoogleProfilePort
                oauthUserResolver,
                tokenProvider,      // GenerateTokenPort
                tokenProvider,      // RefreshTokenHashPort
                refreshTokenStore,  // SaveRefreshTokenHashPort
                new AuthMetricAdapter(meterRegistry)  // RecordAuthMetricPort
        );
        oauthClient.register(AUTH_CODE, new OauthProfile(Provider.KAKAO, KAKAO_ID, KAKAO_EMAIL));
        oauthClient.register(GOOGLE_ID_TOKEN, new OauthProfile(Provider.GOOGLE, GOOGLE_ID, GOOGLE_EMAIL));
        handlerLog = LogCapture.of(OauthLoginHandler.class);
        resolverLog = LogCapture.of(OauthUserResolver.class);
    }

    @AfterEach
    void tearDown() {
        handlerLog.stop();
        resolverLog.stop();
    }

    private OauthLoginResult login() {
        return oauthLoginHandler.handle(new OauthLoginCommand("kakao", null, AUTH_CODE, CODE_VERIFIER));
    }

    private OauthLoginResult googleLogin(String idToken) {
        return oauthLoginHandler.handle(new OauthLoginCommand("google", idToken, null, null));
    }

    @Test
    @DisplayName("처음 소셜 로그인하면 유저가 새로 가입되고 토큰을 받는다")
    void firstOauthLoginRegistersUser() {
        // when
        OauthLoginResult result = login();
        // then
        assertThat(userStore.size()).isEqualTo(1);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getEmail().value()).isEqualTo(KAKAO_EMAIL);
        assertThat(saved.hasProvider(Provider.KAKAO)).isTrue();
    }

    @Test
    @DisplayName("소셜 가입 유저는 비밀번호 없이 만들어진다")
    void oauthUserHasNoPassword() {
        // when
        OauthLoginResult result = login();
        // then
        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getPasswordHash().value()).isEmpty();
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 가입하지 않고 같은 userId를 돌려준다")
    void secondOauthLoginReusesUser() {
        // given
        OauthLoginResult first = login();
        // when
        OauthLoginResult second = login();
        // then
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(userStore.size()).isEqualTo(1);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    }

    @Test
    @DisplayName("소셜 로그인도 refresh token을 해시로 저장한다")
    void oauthLoginStoresHashedRefreshToken() {
        // when
        OauthLoginResult result = login();
        // then
        String storedHash = refreshTokenStore.loadById(result.userId()).orElseThrow();
        assertThat(storedHash).isNotEqualTo(result.refreshToken());
        assertThat(tokenProvider.matches(result.refreshToken(), storedHash)).isTrue();
    }

    @Test
    @DisplayName("이미 로컬 가입된 이메일이면 자동 연동하지 않고 EmailAlreadyExistsException이 발생한다")
    void oauthLoginRejectsExistingLocalEmail() {
        // given - 같은 이메일로 로컬 회원가입이 되어 있다
        signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(KAKAO_EMAIL), "Password123!"));
        // when & then
        assertThatThrownBy(this::login)
                .isInstanceOf(EmailAlreadyExistsException.class);
        // 소셜 유저가 추가로 만들어지지 않는다
        assertThat(userStore.size()).isEqualTo(1);
        // 로컬 가입 때 발급된 것 하나뿐이고, 소셜 로그인 토큰은 저장되지 않는다
        assertThat(refreshTokenStore.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("인가 코드 교환에 실패하면 OauthLoginFailedException이 발생하고 아무것도 저장되지 않는다")
    void oauthLoginWithInvalidAuthorizationCode() {
        // when & then
        assertThatThrownBy(() -> oauthLoginHandler.handle(
                new OauthLoginCommand("kakao", null, "expired-code", CODE_VERIFIER)))
                .isInstanceOf(OauthLoginFailedException.class);

        assertThat(userStore.size()).isZero();
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("구글 ID 토큰으로 처음 로그인하면 구글 계정으로 가입되고 토큰을 받는다")
    void firstGoogleLoginRegistersUser() {
        // when
        OauthLoginResult result = googleLogin(GOOGLE_ID_TOKEN);
        // then
        assertThat(result.accessToken()).isNotBlank();
        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getEmail().value()).isEqualTo(GOOGLE_EMAIL);
        assertThat(saved.hasProvider(Provider.GOOGLE)).isTrue();
    }

    @Test
    @DisplayName("구글 ID 토큰 검증에 실패하면 OauthLoginFailedException이 발생하고 아무것도 저장되지 않는다")
    void googleLoginWithInvalidIdToken() {
        // when & then
        assertThatThrownBy(() -> googleLogin("forged-id-token"))
                .isInstanceOf(OauthLoginFailedException.class);

        assertThat(userStore.size()).isZero();
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("지원하지 않는 provider면 UnsupportedProviderException이 발생하고 아무것도 저장되지 않는다")
    void oauthLoginWithUnsupportedProvider() {
        // when & then
        assertThatThrownBy(() -> oauthLoginHandler.handle(new OauthLoginCommand("naver", null, null, null)))
                .isInstanceOf(UnsupportedProviderException.class);

        assertThat(userStore.size()).isZero();
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("소셜 로그인에 성공하면 userId와 provider를 담아 성공 로그를 남긴다")
    void oauthLoginLogsSuccess() {
        // when
        OauthLoginResult result = login();
        // then
        assertThat(handlerLog.messages(Level.INFO))
                .containsExactly("[인증] 소셜 로그인 성공 - userId=" + result.userId() + ", provider=KAKAO");
    }

    @Test
    @DisplayName("로컬 계정과 이메일이 겹치면 실패 원인을 남기되 이메일과 소셜 회원번호는 남기지 않는다")
    void oauthLoginWithExistingLocalEmailLogsFailure() {
        // given
        signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(KAKAO_EMAIL), "Password123!"));
        // when
        assertThatThrownBy(this::login)
                .isInstanceOf(EmailAlreadyExistsException.class);
        // then
        assertThat(resolverLog.messages(Level.INFO))
                .containsExactly("[인증] 소셜 로그인 실패: 이미 가입된 이메일 - provider=KAKAO");
        assertThat(resolverLog.messages(Level.INFO))
                .noneMatch(message -> message.contains(KAKAO_EMAIL) || message.contains(KAKAO_ID));
        assertThat(handlerLog.messages(Level.INFO)).isEmpty();
    }

    @Test
    @DisplayName("지원하지 않는 provider면 요청한 provider 이름을 담아 실패 로그를 남긴다")
    void oauthLoginWithUnsupportedProviderLogsFailure() {
        // when
        assertThatThrownBy(() -> oauthLoginHandler.handle(new OauthLoginCommand("naver", null, null, null)))
                .isInstanceOf(UnsupportedProviderException.class);
        // then
        assertThat(handlerLog.messages(Level.INFO))
                .containsExactly("[인증] 소셜 로그인 실패: 지원하지 않는 provider - provider=naver");
    }

    @Test
    @DisplayName("대소문자만 다른 provider도 지원하지 않는 provider로 거절하고 unknown으로 센다")
    void oauthLoginWithCaseMismatchedProvider() {
        // when -> 도메인은 소문자 이름과 정확히 같을 때만 인식한다
        assertThatThrownBy(() -> oauthLoginHandler.handle(new OauthLoginCommand("GOOGLE", null, null, null)))
                .isInstanceOf(UnsupportedProviderException.class);

        // then
        assertThat(handlerLog.messages(Level.INFO))
                .containsExactly("[인증] 소셜 로그인 실패: 지원하지 않는 provider - provider=GOOGLE");
        assertThat(oauthLoginCounter("unknown", "failure", "UNSUPPORTED_PROVIDER")).isNotNull();
        assertThat(userStore.size()).isZero();
    }

    @Test
    @DisplayName("provider가 공백뿐이면 도메인 예외 대신 UnsupportedProviderException으로 거절한다")
    void oauthLoginWithBlankProvider() {
        // when & then -> 도메인의 ProviderRequiredException이 그대로 나가면 500으로 가려진다
        assertThatThrownBy(() -> oauthLoginHandler.handle(new OauthLoginCommand(" ", null, null, null)))
                .isInstanceOf(UnsupportedProviderException.class);
    }

    private Counter oauthLoginCounter(String provider, String result, String reason) {
        return meterRegistry.find("runiverse.auth.oauthlogin")
                .tags("provider", provider, "result", result, "reason", reason)
                .counter();
    }

    @Test
    @DisplayName("소셜 로그인에 성공하면 provider별 성공으로 센다")
    void oauthLoginCountsSuccessByProvider() {
        // when
        login();

        // then
        Counter counter = oauthLoginCounter("kakao", "success", "none");
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("외부에서 던진 코드 교환 실패도 provider와 함께 센다")
    void oauthLoginCountsExchangeFailureWithProvider() {
        // when -> 예외는 infra(OAuth 클라이언트)에서 던져져 핸들러를 통과한다
        assertThatThrownBy(() -> oauthLoginHandler.handle(
                new OauthLoginCommand("kakao", null, "expired-code", CODE_VERIFIER)))
                .isInstanceOf(OauthLoginFailedException.class);

        // then
        assertThat(oauthLoginCounter("kakao", "failure", "OAUTH_LOGIN_FAILED")).isNotNull();
    }

    @Test
    @DisplayName("구글 ID 토큰 검증 실패는 google로 센다")
    void googleLoginCountsVerificationFailure() {
        // when
        assertThatThrownBy(() -> googleLogin("forged-id-token"))
                .isInstanceOf(OauthLoginFailedException.class);

        // then
        assertThat(oauthLoginCounter("google", "failure", "OAUTH_LOGIN_FAILED")).isNotNull();
    }

    @Test
    @DisplayName("로컬 계정과 이메일이 겹쳐 실패하면 그 원인을 센다")
    void oauthLoginCountsEmailConflict() {
        // given
        signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(KAKAO_EMAIL), "Password123!"));

        // when -> Resolver 안에서 던진 예외도 핸들러의 한 곳에서 잡힌다
        assertThatThrownBy(this::login).isInstanceOf(EmailAlreadyExistsException.class);

        // then
        assertThat(oauthLoginCounter("kakao", "failure", "EMAIL_ALREADY_EXISTS")).isNotNull();
    }

    @Test
    @DisplayName("지원하지 않는 provider는 요청 값 대신 unknown으로 센다")
    void oauthLoginCountsUnsupportedProviderAsUnknown() {
        // when
        assertThatThrownBy(() -> oauthLoginHandler.handle(new OauthLoginCommand("naver", null, null, null)))
                .isInstanceOf(UnsupportedProviderException.class);

        // then -> 요청 값을 태그로 쓰면 값의 종류가 무한히 늘어난다
        assertThat(oauthLoginCounter("unknown", "failure", "UNSUPPORTED_PROVIDER")).isNotNull();
        assertThat(meterRegistry.find("runiverse.auth.oauthlogin").tag("provider", "naver").counter()).isNull();
    }
}
