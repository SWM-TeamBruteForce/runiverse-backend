package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileHandler;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileResult;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoHandler;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 수정 통합 테스트")
public class ChangeMyProfileIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "완두콩";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final String CHANGED_INTRODUCTION = "천천히 오래 달려요";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");
    private static final int AVG_PACE = 330;

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeMyProfileHandler changeMyProfileHandler;
    private GetMyBasicInfoHandler getMyBasicInfoHandler;

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
                onboardingStore,  // LoadOnboardingPort
                onboardingStore   // UpdateOnboardingPort
        );
        getMyBasicInfoHandler = new GetMyBasicInfoHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // LoadNicknamePort
                userStore         // LoadOauthProviderPort
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

    private ChangeMyProfileResult changeIntroduction(UUID userId, String introduction) {
        return changeMyProfileHandler.handle(
                new ChangeMyProfileCommand(userId, introduction, null, null, null, null));
    }

    @Test
    @DisplayName("소개글을 바꾸면 조회에도 그대로 반영된다")
    void changedIntroductionIsVisible() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        ChangeMyProfileResult result = changeIntroduction(userId, INTRODUCTION);

        // then
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(userStore.findById(userId))
                .hasValueSatisfying(user ->
                        assertThat(user.getIntroduction().value()).isEqualTo(INTRODUCTION));
    }

    @Test
    @DisplayName("빈 문자열로 지우면 소개글이 비워진다")
    void clearsIntroduction() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        changeIntroduction(userId, INTRODUCTION);

        // when -> 편집 화면에서 소개글을 비우고 저장한 경우다
        changeIntroduction(userId, "");

        // then
        assertThat(userStore.findById(userId))
                .hasValueSatisfying(user -> assertThat(user.getIntroduction().value()).isEmpty());
    }

    @Test
    @DisplayName("가입만 한 사용자도 소개글은 바꿀 수 있다")
    void allowsIntroductionBeforeOnboarding() {
        // given -> 소개글은 users에 있어 온보딩과 무관하다
        UUID userId = signUp(EMAIL);

        // when
        ChangeMyProfileResult result = changeIntroduction(userId, INTRODUCTION);

        // then
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(getMyBasicInfoHandler.handle(new GetMyBasicInfoQuery(userId)).isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("온보딩 값만 바꾸면 나머지 온보딩 값과 닉네임은 그대로다")
    void keepsUntouchedOnboardingValues() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        BigDecimal newWeight = new BigDecimal("68.0");

        // when
        changeMyProfileHandler.handle(
                new ChangeMyProfileCommand(userId, null, null, null, newWeight, null));

        // then -> 닉네임과 평균 페이스는 이 API가 다루지 않는다
        assertThat(onboardingStore.findByUserId(userId)).hasValueSatisfying(onboarding -> {
            assertThat(onboarding.getWeight().value()).isEqualByComparingTo(newWeight);
            assertThat(onboarding.getHeight().value()).isEqualByComparingTo(HEIGHT);
            assertThat(onboarding.getBirthday().value()).isEqualTo(BIRTHDAY);
            assertThat(onboarding.getAvgPace().secondPerKm()).isEqualTo(AVG_PACE);
        });
        assertThat(onboardingStore.nicknameOf(userId)).contains(NICKNAME);
    }

    @Test
    @DisplayName("소개글과 온보딩 값을 함께 보내면 둘 다 반영된다")
    void changesBothTables() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        LocalDate newBirthday = LocalDate.of(1998, 12, 16);

        // when
        ChangeMyProfileResult result = changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, CHANGED_INTRODUCTION, "FEMALE", newBirthday, null, null));

        // then -> users와 user_onboardings를 한 트랜잭션으로 바꾼다
        assertThat(result.introduction()).isEqualTo(CHANGED_INTRODUCTION);
        assertThat(result.gender()).isEqualTo("FEMALE");
        assertThat(result.birthday()).isEqualTo(newBirthday);
        assertThat(userStore.findById(userId)).hasValueSatisfying(user ->
                assertThat(user.getIntroduction().value()).isEqualTo(CHANGED_INTRODUCTION));
    }

    @Test
    @DisplayName("온보딩 전에 온보딩 값을 보내면 소개글도 바뀌지 않는다")
    void rejectsOnboardingFieldsBeforeOnboarding() {
        // given -> 절반만 저장되면 클라이언트가 그것을 알 방법이 없다
        UUID userId = signUp(EMAIL);

        // when & then
        assertThatThrownBy(() -> changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, INTRODUCTION, null, null, WEIGHT, null)))
                .isInstanceOf(OnboardingNotCompletedException.class);
        assertThat(userStore.findById(userId))
                .hasValueSatisfying(user -> assertThat(user.getIntroduction().value()).isEmpty());
    }

    @Test
    @DisplayName("다른 사용자의 프로필은 건드리지 않는다")
    void doesNotAffectOtherUsers() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        changeIntroduction(otherUserId, INTRODUCTION);

        // when
        changeIntroduction(userId, CHANGED_INTRODUCTION);

        // then
        assertThat(userStore.findById(userId)).hasValueSatisfying(user ->
                assertThat(user.getIntroduction().value()).isEqualTo(CHANGED_INTRODUCTION));
        assertThat(userStore.findById(otherUserId)).hasValueSatisfying(user ->
                assertThat(user.getIntroduction().value()).isEqualTo(INTRODUCTION));
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 바꿀 수 없다")
    void throwsForUnknownUser() {
        // when & then
        assertThatThrownBy(() -> changeIntroduction(UuidCreator.getTimeOrderedEpoch(), INTRODUCTION))
                .isInstanceOf(UserNotFoundException.class);
    }
}
