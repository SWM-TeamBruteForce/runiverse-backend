package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginHandler;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthUserResolver;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.account.GetMyAccountHandler;
import com.runiverse.running_service.application.user.query.account.GetMyAccountQuery;
import com.runiverse.running_service.application.user.query.account.GetMyAccountResult;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("내 계정 정보 조회 통합 테스트")
public class GetMyAccountIntegrationTest extends IntegrationTestSupport {

    private static final String LOCAL_EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String KAKAO_CODE = "kakao-authorization-code";
    private static final String KAKAO_ID = "1234567890";
    private static final String KAKAO_EMAIL = "runner@kakao.com";
    private static final String GOOGLE_CODE = "google-authorization-code";
    private static final String GOOGLE_ID = "9876543210";
    private static final String GOOGLE_EMAIL = "runner@gmail.com";
    private static final String CODE_VERIFIER = "pkce-code-verifier";

    private SignUpHandler signUpHandler;
    private OauthLoginHandler oauthLoginHandler;
    private GetMyAccountHandler getMyAccountHandler;

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
                oauthClient,       // ExchangeOauthCodePort
                oauthUserResolver,
                tokenProvider,     // GenerateTokenPort
                tokenProvider,     // RefreshTokenHashPort
                refreshTokenStore  // SaveRefreshTokenHashPort
        );
        getMyAccountHandler = new GetMyAccountHandler(
                userStore,  // LoadUserByIdPort
                userStore   // LoadOauthProviderPort
        );
        oauthClient.register(KAKAO_CODE, new OauthProfile(Provider.KAKAO, KAKAO_ID, KAKAO_EMAIL));
        oauthClient.register(GOOGLE_CODE, new OauthProfile(Provider.GOOGLE, GOOGLE_ID, GOOGLE_EMAIL));
    }

    private UUID signUp() {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(LOCAL_EMAIL), PASSWORD)).userId();
    }

    private UUID oauthLogin(String provider, String code) {
        return oauthLoginHandler.handle(new OauthLoginCommand(provider, code, CODE_VERIFIER)).userId();
    }

    private GetMyAccountResult accountOf(UUID userId) {
        return getMyAccountHandler.handle(new GetMyAccountQuery(userId));
    }

    @Test
    @DisplayName("로컬로 가입한 사용자는 가입 이메일과 LOCAL을 받는다")
    void reportsLocalForSignUpUser() {
        // given
        UUID userId = signUp();

        // when
        GetMyAccountResult result = accountOf(userId);

        // then -> 클라는 LOCAL일 때만 비밀번호 변경 메뉴를 노출한다
        assertThat(result.email()).isEqualTo(LOCAL_EMAIL);
        assertThat(result.loginType()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("카카오로 가입한 사용자는 카카오 이메일과 KAKAO를 받는다")
    void reportsKakaoForKakaoUser() {
        // given
        UUID userId = oauthLogin("kakao", KAKAO_CODE);

        // when
        GetMyAccountResult result = accountOf(userId);

        // then
        assertThat(result.email()).isEqualTo(KAKAO_EMAIL);
        assertThat(result.loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("구글로 가입한 사용자는 구글 이메일과 GOOGLE을 받는다")
    void reportsGoogleForGoogleUser() {
        // given
        UUID userId = oauthLogin("google", GOOGLE_CODE);

        // when
        GetMyAccountResult result = accountOf(userId);

        // then
        assertThat(result.email()).isEqualTo(GOOGLE_EMAIL);
        assertThat(result.loginType()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("다른 사용자의 로그인 수단이 내 판정에 섞이지 않는다")
    void otherUsersProviderDoesNotLeak() {
        // given -> 로컬과 소셜 계정이 함께 있는 상태
        UUID localUserId = signUp();
        UUID kakaoUserId = oauthLogin("kakao", KAKAO_CODE);

        // when & then
        assertThat(accountOf(localUserId).loginType()).isEqualTo("LOCAL");
        assertThat(accountOf(kakaoUserId).loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 토큰은 유효하지만 계정이 없는 경우다
        assertThatThrownBy(() -> accountOf(UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
