package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.OnboardingAlreadyCompletedException;
import com.runiverse.running_service.domain.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.user.vo.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserOnboardingTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";
    private static final String NICKNAME = "러너킴";
    private static final String GENDER = "MALE";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    private User onboardedUser() {
        User user = new User(USER_ID, EMAIL, PASSWORD_HASH);
        user.completeOnboarding(NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT);
        return user;
    }

    @Nested
    @DisplayName("온보딩 완료 테스트")
    class CompleteOnboardingTest {

        @Test
        @DisplayName("온보딩을 완료하면 유저가 온보딩 정보를 갖는다")
        void completeOnboardingSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when
            user.completeOnboarding(NICKNAME, GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT);

            // then
            assertThat(user.hasOnboarded()).isTrue();
            assertThat(user.getOnboarding()).isPresent();
            assertThat(user.getOnboarding().orElseThrow().getNickname().value()).isEqualTo(NICKNAME);
            assertThat(user.getOnboarding().orElseThrow().getUserId()).isEqualTo(user.getUserId());
        }

        @Test
        @DisplayName("가입 직후 유저는 온보딩을 하지 않은 상태다")
        void newUserHasNotOnboarded() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThat(user.hasOnboarded()).isFalse();
            assertThat(user.getOnboarding()).isEmpty();
        }

        @Test
        @DisplayName("이미 온보딩한 유저는 다시 완료할 수 없다")
        void completeOnboardingTwiceFails() {
            // given
            User user = onboardedUser();

            // when & then -> 조건이 반대면 기존 온보딩 값이 조용히 덮어써진다
            assertThatThrownBy(() -> user.completeOnboarding(
                    "다른러너", "FEMALE", LocalDate.of(2000, 1, 1),
                    400, new BigDecimal("60.0"), new BigDecimal("165.0")))
                    .isInstanceOf(OnboardingAlreadyCompletedException.class)
                    .hasMessage("이미 온보딩을 완료했습니다.");

            assertThat(user.getOnboarding().orElseThrow().getNickname().value()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("잘못된 값으로 온보딩하면 예외가 발생하고 상태는 그대로다")
        void completeOnboardingWithInvalidValueFails() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThatThrownBy(() -> user.completeOnboarding(
                    "김", GENDER, BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT))
                    .isInstanceOf(InvalidNicknameLengthException.class);

            assertThat(user.hasOnboarded()).isFalse();
        }
    }

    @Nested
    @DisplayName("온보딩 수정 테스트")
    class UpdateOnboardingTest {

        @Test
        @DisplayName("온보딩 정보를 수정할 수 있다")
        void updateOnboardingSuccess() {
            // given
            User user = onboardedUser();

            // when
            user.updateOnboarding("새러너", "FEMALE", null, 400, null, null);

            // then
            assertThat(user.getOnboarding().orElseThrow().getNickname().value()).isEqualTo("새러너");
            assertThat(user.getOnboarding().orElseThrow().getGender()).isEqualTo(Gender.FEMALE);
            assertThat(user.getOnboarding().orElseThrow().getAvgPace().secondPerKm()).isEqualTo(400);
        }

        @Test
        @DisplayName("전달하지 않은 항목은 기존 값이 유지된다")
        void updateOnboardingKeepsUntouchedFields() {
            // given
            User user = onboardedUser();

            // when -> 닉네임만 바꾼다
            user.updateOnboarding("새러너", null, null, null, null, null);

            // then
            assertThat(user.getOnboarding().orElseThrow().getBirthday().value()).isEqualTo(BIRTHDAY);
            assertThat(user.getOnboarding().orElseThrow().getWeight().value())
                    .isEqualByComparingTo(WEIGHT);
            assertThat(user.getOnboarding().orElseThrow().getHeight().value())
                    .isEqualByComparingTo(HEIGHT);
        }

        @Test
        @DisplayName("온보딩하지 않은 유저는 수정할 수 없다")
        void updateOnboardingBeforeCompleteFails() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThatThrownBy(() ->
                    user.updateOnboarding("새러너", null, null, null, null, null))
                    .isInstanceOf(OnboardingNotCompletedException.class)
                    .hasMessage("온보딩을 먼저 완료해야 합니다.");
        }

        @Test
        @DisplayName("잘못된 값으로 수정하면 예외가 발생하고 기존 값이 유지된다")
        void updateOnboardingWithInvalidValueFails() {
            // given
            User user = onboardedUser();

            // when & then
            assertThatThrownBy(() ->
                    user.updateOnboarding("김", null, null, null, null, null))
                    .isInstanceOf(InvalidNicknameLengthException.class);

            assertThat(user.getOnboarding().orElseThrow().getNickname().value()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("수정해도 온보딩의 userId는 유지된다")
        void updateOnboardingKeepsUserId() {
            // given
            User user = onboardedUser();

            // when
            user.updateOnboarding("새러너", null, null, null, null, null);

            // then
            assertThat(user.getOnboarding().orElseThrow().getUserId()).isEqualTo(user.getUserId());
        }
    }
}
