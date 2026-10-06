package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileHandler;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileHandler;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileResult;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 편집용 조회 통합 테스트")
public class GetMyProfileIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "완두콩";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");
    private static final int AVG_PACE = 330;

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeMyProfileHandler changeMyProfileHandler;
    private GetMyProfileHandler getMyProfileHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        changeMyProfileHandler = new ChangeMyProfileHandler(
                userStore,        // LoadUserByIdPort
                userStore,        // UpdateIntroductionPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore   // UpdateOnboardingPort
        );
        getMyProfileHandler = new GetMyProfileHandler(
                userStore,       // LoadUserByIdPort
                onboardingStore  // LoadOnboardingProfilePort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUp(email);
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT));
        return userId;
    }

    private GetMyProfileResult profileOf(UUID userId) {
        return getMyProfileHandler.handle(new GetMyProfileQuery(userId));
    }

    @Test
    @DisplayName("가입만 마친 사용자도 조회할 수 있고 온보딩 값은 비어 있다")
    void returnsEmptyProfileRightAfterSignUp() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetMyProfileResult result = profileOf(userId);

        // then -> 온보딩 전에도 편집 화면이 열리므로 막지 않는다
        assertThat(result.introduction()).isNull();
        assertThat(result.gender()).isNull();
        assertThat(result.birthday()).isNull();
        assertThat(result.weightKg()).isNull();
        assertThat(result.heightCm()).isNull();
    }

    @Test
    @DisplayName("온보딩을 마치면 그때 넣은 값이 그대로 나온다")
    void returnsValuesEnteredAtOnboarding() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        GetMyProfileResult result = profileOf(userId);

        // then -> 편집 화면의 입력 칸을 이 값으로 채운다
        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.birthday()).isEqualTo(BIRTHDAY);
        assertThat(result.weightKg()).isEqualTo(WEIGHT);
        assertThat(result.heightCm()).isEqualTo(HEIGHT);
    }

    @Test
    @DisplayName("프로필을 수정하면 바뀐 값으로 답한다")
    void reflectsChangedProfile() {
        // given -> 프로필 수정과 같은 필드 집합이라 고친 값이 바로 보여야 한다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        BigDecimal changedWeight = new BigDecimal("68.0");

        // when
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, INTRODUCTION, "FEMALE", null, changedWeight, null));

        // then -> 보내지 않은 생일·키는 온보딩 때 값 그대로다
        GetMyProfileResult result = profileOf(userId);
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.gender()).isEqualTo("FEMALE");
        assertThat(result.weightKg()).isEqualTo(changedWeight);
        assertThat(result.birthday()).isEqualTo(BIRTHDAY);
        assertThat(result.heightCm()).isEqualTo(HEIGHT);
    }

    @Test
    @DisplayName("소개글은 온보딩 전에 바꿔도 조회에 반영된다")
    void reflectsIntroductionChangedBeforeOnboarding() {
        // given -> 소개글은 users에 있어 온보딩과 무관하게 바뀐다
        UUID userId = signUp(EMAIL);

        // when
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, INTRODUCTION, null, null, null, null));

        // then
        GetMyProfileResult result = profileOf(userId);
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.gender()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 값이 섞이지 않는다")
    void doesNotLeakOtherUsersProfile() {
        // given
        UUID userId = signUp(EMAIL);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                otherUserId, INTRODUCTION, null, null, null, null));

        // when & then
        assertThat(profileOf(userId).introduction()).isNull();
        assertThat(profileOf(userId).gender()).isNull();
        assertThat(profileOf(otherUserId).introduction()).isEqualTo(INTRODUCTION);
        assertThat(profileOf(otherUserId).gender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 토큰은 유효하지만 계정이 없는 경우다
        assertThatThrownBy(() -> profileOf(UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
