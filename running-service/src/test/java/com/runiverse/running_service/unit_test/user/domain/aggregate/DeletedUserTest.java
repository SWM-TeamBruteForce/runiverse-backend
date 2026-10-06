package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.user.DeletedUser;
import com.runiverse.running_service.domain.user.exception.BmiOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.BmiRequiredException;
import com.runiverse.running_service.domain.user.exception.GenderRequiredException;
import com.runiverse.running_service.domain.user.exception.JoinedAtRequiredException;
import com.runiverse.running_service.domain.user.exception.LoginTypeRequiredException;
import com.runiverse.running_service.domain.user.exception.NicknameRequiredException;
import com.runiverse.running_service.domain.user.vo.Bmi;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeletedUserTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String EMAIL = "runner@example.com";
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Nested
    @DisplayName("BMI 테스트")
    class BmiTest {

        @Test
        @DisplayName("체중과 신장으로 BMI를 계산한다")
        void calculatesBmiFromWeightAndHeight() {
            // when -> 70kg / 1.75m^2 = 22.857...
            Bmi bmi = Bmi.from(new Weight(new BigDecimal("70.0")), new Height(new BigDecimal("175.0")));

            // then
            assertThat(bmi.value()).isEqualByComparingTo("22.9");
        }

        @Test
        @DisplayName("반올림은 마지막 나눗셈에서 한 번만 한다")
        void roundsOnlyOnceAtTheEnd() {
            // given -> 중간에 반올림하면 결과가 달라지는 값이다.
            //          60 * 10000 / (163.5 * 163.5) = 22.4459...
            Weight weight = new Weight(new BigDecimal("60.0"));
            Height height = new Height(new BigDecimal("163.5"));

            // when
            Bmi bmi = Bmi.from(weight, height);

            // then -> 22.4다. 중간 반올림이 섞이면 22.5가 된다
            assertThat(bmi.value()).isEqualByComparingTo("22.4");
        }

        @Test
        @DisplayName("소수점 첫째 자리로 정규화된다")
        void normalizesToOneDecimalPlace() {
            // when
            Bmi bmi = new Bmi(new BigDecimal("22.45"));

            // then
            assertThat(bmi.value().scale()).isEqualTo(1);
            assertThat(bmi.value()).isEqualByComparingTo("22.5");
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void nullBmiFails() {
            // when & then
            assertThatThrownBy(() -> new Bmi(null))
                    .isInstanceOf(BmiRequiredException.class);
        }

        @Test
        @DisplayName("0 이하이면 예외가 발생한다")
        void nonPositiveBmiFails() {
            // when & then -> 반올림해서 0이 되는 값도 막는다. 저장될 값이 0.0이기 때문이다
            assertThatThrownBy(() -> new Bmi(new BigDecimal("-5.0")))
                    .isInstanceOf(BmiOutOfRangeException.class);
            assertThatThrownBy(() -> new Bmi(new BigDecimal("0.04")))
                    .isInstanceOf(BmiOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("온보딩을 마친 계정 스냅샷 테스트")
    class OnboardedTest {

        @Test
        @DisplayName("체중과 신장 대신 BMI를, 생년월일 대신 연도를 담는다")
        void keepsDerivedValuesOnly() {
            // when
            DeletedUser deleted = onboarded();

            // then -> 원값을 복원할 수 없어야 한다
            assertThat(deleted.getBmi()).contains(
                    Bmi.from(new Weight(new BigDecimal("70.0")), new Height(new BigDecimal("175.0"))));
            assertThat(deleted.getBirthYear()).contains(1995);
        }

        @Test
        @DisplayName("계정 정보와 온보딩 값을 모두 갖는다")
        void keepsAccountAndOnboardingValues() {
            // when
            DeletedUser deleted = onboarded();

            // then
            assertThat(deleted.getUserId().value()).isEqualTo(USER_ID);
            assertThat(deleted.getEmail().value()).isEqualTo(EMAIL);
            assertThat(deleted.getLoginType()).isEqualTo(LoginType.KAKAO);
            assertThat(deleted.getJoinedAt()).isEqualTo(JOINED_AT);
            assertThat(deleted.getNickname()).isPresent();
            assertThat(deleted.getGender()).contains(Gender.MALE);
            assertThat(deleted.getAvgPace()).isPresent();
        }

        @Test
        @DisplayName("성별이 없으면 예외가 발생한다")
        void nullGenderFails() {
            // when & then -> enum이라 감쌀 VO가 없어 팩토리가 직접 막는다
            assertThatThrownBy(() -> DeletedUser.ofOnboarded(
                    USER_ID, EMAIL, "러너킴", null, LocalDate.of(1995, 3, 1),
                    330, new BigDecimal("70.0"), new BigDecimal("175.0"),
                    LoginType.KAKAO, JOINED_AT))
                    .isInstanceOf(GenderRequiredException.class);
        }

        @Test
        @DisplayName("닉네임이 없으면 예외가 발생한다")
        void nullNicknameFails() {
            // when & then -> 나머지 온보딩 값은 VO 생성자가 막는다
            assertThatThrownBy(() -> DeletedUser.ofOnboarded(
                    USER_ID, EMAIL, null, Gender.MALE, LocalDate.of(1995, 3, 1),
                    330, new BigDecimal("70.0"), new BigDecimal("175.0"),
                    LoginType.KAKAO, JOINED_AT))
                    .isInstanceOf(NicknameRequiredException.class);
        }
    }

    @Nested
    @DisplayName("온보딩 전 계정 스냅샷 테스트")
    class NotOnboardedTest {

        @Test
        @DisplayName("온보딩 값 다섯이 모두 비어 있다")
        void leavesAllOnboardingValuesEmpty() {
            // when
            DeletedUser deleted = DeletedUser.ofNotOnboarded(
                    USER_ID, EMAIL, LoginType.LOCAL, JOINED_AT);

            // then -> 함께 채워지고 함께 빈다
            assertThat(deleted.getNickname()).isEmpty();
            assertThat(deleted.getGender()).isEmpty();
            assertThat(deleted.getBirthYear()).isEmpty();
            assertThat(deleted.getAvgPace()).isEmpty();
            assertThat(deleted.getBmi()).isEmpty();
        }

        @Test
        @DisplayName("가입 수단과 가입 시각은 남는다")
        void keepsLoginTypeAndJoinedAt() {
            // when -> 통계에 쓸 체류 기간을 내려면 둘이 필요하다
            DeletedUser deleted = DeletedUser.ofNotOnboarded(
                    USER_ID, EMAIL, LoginType.LOCAL, JOINED_AT);

            // then
            assertThat(deleted.getLoginType()).isEqualTo(LoginType.LOCAL);
            assertThat(deleted.getJoinedAt()).isEqualTo(JOINED_AT);
        }

        @Test
        @DisplayName("가입 수단이 없으면 예외가 발생한다")
        void nullLoginTypeFails() {
            // when & then
            assertThatThrownBy(() -> DeletedUser.ofNotOnboarded(USER_ID, EMAIL, null, JOINED_AT))
                    .isInstanceOf(LoginTypeRequiredException.class);
        }

        @Test
        @DisplayName("가입 시각이 없으면 예외가 발생한다")
        void nullJoinedAtFails() {
            // when & then
            assertThatThrownBy(() -> DeletedUser.ofNotOnboarded(
                    USER_ID, EMAIL, LoginType.LOCAL, null))
                    .isInstanceOf(JoinedAtRequiredException.class);
        }
    }

    private DeletedUser onboarded() {
        return DeletedUser.ofOnboarded(
                USER_ID, EMAIL, "러너킴", Gender.MALE, LocalDate.of(1995, 3, 1),
                330, new BigDecimal("70.0"), new BigDecimal("175.0"),
                LoginType.KAKAO, JOINED_AT);
    }
}
