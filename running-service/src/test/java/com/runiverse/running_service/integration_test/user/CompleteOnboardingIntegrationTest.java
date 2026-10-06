package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;
import com.runiverse.running_service.application.user.exception.AlreadyOnboardedException;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryOnboardingStore.OnboardingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("온보딩 완료 통합 테스트")
public class CompleteOnboardingIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String GENDER = "MALE";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,     // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private CompleteOnboardingCommand command(UUID userId, String nickname) {
        return new CompleteOnboardingCommand(
                userId, nickname, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT);
    }

    @Test
    @DisplayName("온보딩을 완료하면 입력한 값이 VO로 저장된다")
    void completeOnboardingSuccess() {
        // given
        UUID userId = signUp(EMAIL);
        // when
        CompleteOnboardingResult result = completeOnboardingHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.nickname()).isEqualTo(NICKNAME);

        OnboardingRow saved = onboardingStore.findByUserId(userId).orElseThrow();
        assertThat(saved.getNickname().value()).isEqualTo(NICKNAME);
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getBirthday().value()).isEqualTo(BIRTHDAY);
        assertThat(saved.getAvgPace().secondPerKm()).isEqualTo(AVG_PACE);
        assertThat(saved.getWeight().value()).isEqualByComparingTo(WEIGHT);
        assertThat(saved.getHeight().value()).isEqualByComparingTo(HEIGHT);
    }

    @Test
    @DisplayName("유저가 온보딩 정보를 갖게 된다")
    void userAggregateHoldsOnboarding() {
        // given
        UUID userId = signUp(EMAIL);
        // when
        completeOnboardingHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(userStore.findById(userId).orElseThrow().hasOnboarded()).isTrue();
    }

    @Test
    @DisplayName("가입하지 않은 userId면 UserNotFoundException이 발생한다")
    void onboardingWithUnknownUser() {
        // when & then
        assertThatThrownBy(() -> completeOnboardingHandler.handle(
                command(UuidCreator.getTimeOrderedEpoch(), NICKNAME)))
                .isInstanceOf(UserNotFoundException.class);

        assertThat(onboardingStore.size()).isZero();
    }

    @Test
    @DisplayName("이미 온보딩한 유저가 다시 시도하면 AlreadyOnboardedException이 발생한다")
    void onboardingTwice() {
        // given
        UUID userId = signUp(EMAIL);
        completeOnboardingHandler.handle(command(userId, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardingHandler.handle(command(userId, "새러너")))
                .isInstanceOf(AlreadyOnboardedException.class);
        // 기존 닉네임이 덮어써지지 않는다
        assertThat(onboardingStore.findByUserId(userId).orElseThrow().getNickname().value())
                .isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("다른 유저가 쓰고 있는 닉네임이면 NicknameAlreadyExistsException이 발생한다")
    void onboardingWithDuplicateNickname() {
        // given
        UUID first = signUp(EMAIL);
        UUID second = signUp("other@runiverse.com");
        completeOnboardingHandler.handle(command(first, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardingHandler.handle(command(second, NICKNAME)))
                .isInstanceOf(NicknameAlreadyExistsException.class);

        assertThat(onboardingStore.size()).isEqualTo(1);
        assertThat(userStore.findById(second).orElseThrow().hasOnboarded()).isFalse();
    }

    @Test
    @DisplayName("닉네임 앞뒤 공백은 제거된 뒤 중복 검사에 걸린다")
    void nicknameIsNormalizedBeforeDuplicateCheck() {
        // given
        UUID first = signUp(EMAIL);
        UUID second = signUp("other@runiverse.com");
        completeOnboardingHandler.handle(command(first, NICKNAME));
        // when & then
        assertThatThrownBy(() -> completeOnboardingHandler.handle(command(second, "  " + NICKNAME + "  ")))
                .isInstanceOf(NicknameAlreadyExistsException.class);
    }

    @Test
    @DisplayName("닉네임이 규칙에 어긋나면 도메인 예외가 전파되고 아무것도 저장되지 않는다")
    void onboardingWithInvalidNickname() {
        // given
        UUID userId = signUp(EMAIL);
        // when & then - 2자 미만
        assertThatThrownBy(() -> completeOnboardingHandler.handle(command(userId, "킴")))
                .isInstanceOf(InvalidNicknameLengthException.class);

        assertThat(onboardingStore.size()).isZero();
        assertThat(userStore.findById(userId).orElseThrow().hasOnboarded()).isFalse();
    }

    @Test
    @DisplayName("실패한 뒤 올바른 값으로 다시 시도하면 온보딩할 수 있다")
    void retryAfterValidationFailure() {
        // given
        UUID userId = signUp(EMAIL);
        assertThatThrownBy(() -> completeOnboardingHandler.handle(command(userId, "킴")))
                .isInstanceOf(InvalidNicknameLengthException.class);
        // when
        CompleteOnboardingResult result = completeOnboardingHandler.handle(command(userId, NICKNAME));
        // then
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(onboardingStore.size()).isEqualTo(1);
    }
}
