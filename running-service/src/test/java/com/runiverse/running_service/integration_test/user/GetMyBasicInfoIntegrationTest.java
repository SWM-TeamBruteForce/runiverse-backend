package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.infrastructure.metrics.AuthMetricAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginHandler;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthUserResolver;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoHandler;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoResult;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("내 기본 정보 조회 통합 테스트")
public class GetMyBasicInfoIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String NEW_NICKNAME = "동완러너";
    private static final String KAKAO_CODE = "kakao-authorization-code";
    private static final String KAKAO_ID = "1234567890";
    private static final String KAKAO_EMAIL = "runner@kakao.com";
    private static final String GOOGLE_ID_TOKEN = "google-id-token";
    private static final String GOOGLE_ID = "9876543210";
    private static final String GOOGLE_EMAIL = "runner@gmail.com";
    private static final String CODE_VERIFIER = "pkce-code-verifier";

    private SignUpHandler signUpHandler;
    private OauthLoginHandler oauthLoginHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeNicknameHandler changeNicknameHandler;
    private GetMyBasicInfoHandler getMyBasicInfoHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        OauthUserResolver oauthUserResolver = new OauthUserResolver(
                userStore,        // LoadUserByProviderPort
                userStore,        // CheckEmailDuplicatePort
                userIdGenerator,  // GenerateUserIdPort
                userStore         // SaveUserPort
        );
        oauthLoginHandler = new OauthLoginHandler(
                oauthClient,       // LoadKakaoProfilePort
                oauthClient,       // LoadGoogleProfilePort
                oauthUserResolver,
                tokenProvider,     // GenerateTokenPort
                tokenProvider,     // RefreshTokenHashPort
                refreshTokenStore, // SaveRefreshTokenHashPort
                new AuthMetricAdapter(new SimpleMeterRegistry())  // RecordAuthMetricPort
        );
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        changeNicknameHandler = new ChangeNicknameHandler(
                onboardingStore,  // LoadNicknamePort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // UpdateNicknamePort
        );
        getMyBasicInfoHandler = new GetMyBasicInfoHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // LoadNicknamePort
                userStore         // LoadOauthProviderPort
        );
        oauthClient.register(KAKAO_CODE, new OauthProfile(Provider.KAKAO, KAKAO_ID, KAKAO_EMAIL));
        oauthClient.register(GOOGLE_ID_TOKEN,new OauthProfile(Provider.GOOGLE, GOOGLE_ID, GOOGLE_EMAIL));
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private UUID kakaoLogin() {
        return oauthLoginHandler.handle(new OauthLoginCommand("kakao", null, KAKAO_CODE, CODE_VERIFIER)).userId();
    }

    private UUID googleLogin() {
        return oauthLoginHandler.handle(new OauthLoginCommand("google", GOOGLE_ID_TOKEN, null, null)).userId();
    }

    private void completeOnboarding(UUID userId, String nickname) {
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")));
    }

    private GetMyBasicInfoResult basicInfoOf(UUID userId) {
        return getMyBasicInfoHandler.handle(new GetMyBasicInfoQuery(userId));
    }

    @Test
    @DisplayName("가입만 마친 사용자는 온보딩 미완료로 답한다")
    void reportsNotOnboardedRightAfterSignUp() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetMyBasicInfoResult result = basicInfoOf(userId);

        // then -> 앱은 이 값을 보고 홈이 아니라 온보딩 화면으로 보낸다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isOnboarded()).isFalse();
        assertThat(result.nickname()).isNull();
    }

    @Test
    @DisplayName("온보딩을 마치면 같은 사용자가 완료로 바뀌고 닉네임이 따라온다")
    void reportsOnboardedAfterOnboarding() {
        // given
        UUID userId = signUp(EMAIL);
        assertThat(basicInfoOf(userId).isOnboarded()).isFalse();

        // when
        completeOnboarding(userId, NICKNAME);

        // then -> 온보딩 완료 판정의 유일한 경로가 이 API다
        GetMyBasicInfoResult result = basicInfoOf(userId);
        assertThat(result.isOnboarded()).isTrue();
        assertThat(result.nickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 바뀐 닉네임으로 답한다")
    void reflectsChangedNickname() {
        // given -> 닉네임 변경은 user_onboardings의 컬럼만 갱신한다
        UUID userId = signUp(EMAIL);
        completeOnboarding(userId, NICKNAME);

        // when
        changeNicknameHandler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then -> 온보딩 시점 스냅샷이 아니라 현재 닉네임을 읽어야 한다
        assertThat(basicInfoOf(userId).nickname()).isEqualTo(NEW_NICKNAME);
    }

    @Test
    @DisplayName("다른 사용자의 온보딩은 내 판정에 영향을 주지 않는다")
    void otherUsersOnboardingDoesNotLeak() {
        // given
        UUID userId = signUp(EMAIL);
        UUID otherUserId = signUp(OTHER_EMAIL);
        completeOnboarding(otherUserId, NICKNAME);

        // when & then
        assertThat(basicInfoOf(userId).isOnboarded()).isFalse();
        assertThat(basicInfoOf(otherUserId).isOnboarded()).isTrue();
    }

    @Test
    @DisplayName("로컬로 가입한 사용자는 가입 이메일과 LOCAL을 받는다")
    void reportsLocalForSignUpUser() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetMyBasicInfoResult result = basicInfoOf(userId);

        // then -> 클라는 LOCAL일 때만 비밀번호 변경 메뉴를 노출한다
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.loginType()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("카카오로 가입한 사용자는 카카오 이메일과 KAKAO를 받는다")
    void reportsKakaoForKakaoUser() {
        // given
        UUID userId = kakaoLogin();

        // when
        GetMyBasicInfoResult result = basicInfoOf(userId);

        // then
        assertThat(result.email()).isEqualTo(KAKAO_EMAIL);
        assertThat(result.loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("구글로 가입한 사용자는 구글 이메일과 GOOGLE을 받는다")
    void reportsGoogleForGoogleUser() {
        // given
        UUID userId = googleLogin();

        // when
        GetMyBasicInfoResult result = basicInfoOf(userId);

        // then
        assertThat(result.email()).isEqualTo(GOOGLE_EMAIL);
        assertThat(result.loginType()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("다른 사용자의 로그인 수단이 내 판정에 섞이지 않는다")
    void otherUsersProviderDoesNotLeak() {
        // given -> 로컬과 소셜 계정이 함께 있는 상태
        UUID localUserId = signUp(EMAIL);
        UUID kakaoUserId = kakaoLogin();

        // when & then
        assertThat(basicInfoOf(localUserId).loginType()).isEqualTo("LOCAL");
        assertThat(basicInfoOf(kakaoUserId).loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 토큰은 유효하지만 계정이 없는 경우다
        assertThatThrownBy(() -> basicInfoOf(UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
