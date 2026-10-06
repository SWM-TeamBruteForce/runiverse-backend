package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.exception.AvgPaceOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.BirthdayRequiredException;
import com.runiverse.running_service.domain.user.exception.GenderRequiredException;
import com.runiverse.running_service.domain.user.exception.HeightOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.HeightRequiredException;
import com.runiverse.running_service.domain.user.exception.InvalidBirthdayException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.NicknameRequiredException;
import com.runiverse.running_service.domain.user.exception.UnsupportedGenderException;
import com.runiverse.running_service.domain.user.exception.WeightOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.WeightRequiredException;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OnboardingVoTest {

    @Nested
    @DisplayName("닉네임 테스트")
    class NicknameTest {

        @Test
        @DisplayName("한글, 영문, 숫자, _로 닉네임을 만들 수 있다")
        void createNicknameSuccess() {
            // when
            Nickname nickname = new Nickname("러너_Kim99");

            // then
            assertThat(nickname.value()).isEqualTo("러너_Kim99");
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void nicknameIsTrimmed() {
            // when
            Nickname nickname = new Nickname("  러너킴  ");

            // then
            assertThat(nickname.value()).isEqualTo("러너킴");
        }

        @Test
        @DisplayName("2자와 16자는 허용된다")
        void nicknameBoundaryLengthSuccess() {
            // when & then -> 경계를 잘못 잡으면 정상 사용자가 막힌다
            assertThat(new Nickname("러너").value()).hasSize(2);
            assertThat(new Nickname("a".repeat(16)).value()).hasSize(16);
        }

        @Test
        @DisplayName("1자이거나 17자면 예외가 발생한다")
        void nicknameOutOfLengthFails() {
            // when & then
            assertThatThrownBy(() -> new Nickname("김"))
                    .isInstanceOf(InvalidNicknameLengthException.class)
                    .hasMessage("닉네임은 2자 이상 16자 이하여야 합니다.");

            assertThatThrownBy(() -> new Nickname("a".repeat(17)))
                    .isInstanceOf(InvalidNicknameLengthException.class);
        }

        @Test
        @DisplayName("공백을 제거한 뒤 길이를 검사한다")
        void nicknameLengthIsCheckedAfterTrim() {
            // when & then -> 앞뒤 공백으로 최소 길이를 채우는 우회를 막는다
            assertThatThrownBy(() -> new Nickname("  러  "))
                    .isInstanceOf(InvalidNicknameLengthException.class);
        }

        @Test
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void nicknameRequiredFails() {
            // when & then
            assertThatThrownBy(() -> new Nickname(null))
                    .isInstanceOf(NicknameRequiredException.class)
                    .hasMessage("닉네임은 필수입니다.");

            assertThatThrownBy(() -> new Nickname("   "))
                    .isInstanceOf(NicknameRequiredException.class);
        }

        @Test
        @DisplayName("허용되지 않은 문자가 있으면 예외가 발생한다")
        void nicknameInvalidFormatFails() {
            // when & then -> 자모 단독, 공백, 특수문자는 모두 거부한다
            assertThatThrownBy(() -> new Nickname("ㅋㅋㅋ"))
                    .isInstanceOf(InvalidNicknameFormatException.class)
                    .hasMessage("닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.");

            assertThatThrownBy(() -> new Nickname("김 러너"))
                    .isInstanceOf(InvalidNicknameFormatException.class);

            assertThatThrownBy(() -> new Nickname("러너!!"))
                    .isInstanceOf(InvalidNicknameFormatException.class);
        }

        @Test
        @DisplayName("같은 문자열이면 같은 값 객체이다")
        void nicknameEquality() {
            // when & then
            assertThat(new Nickname("러너킴")).isEqualTo(new Nickname("러너킴"));
            assertThat(new Nickname("러너킴")).hasSameHashCodeAs(new Nickname("러너킴"));
        }
    }

    @Nested
    @DisplayName("성별 테스트")
    class GenderTest {

        @Test
        @DisplayName("대소문자와 공백에 관계없이 성별을 만들 수 있다")
        void genderFromSuccess() {
            // when & then
            assertThat(Gender.from("MALE")).isEqualTo(Gender.MALE);
            assertThat(Gender.from("female")).isEqualTo(Gender.FEMALE);
            assertThat(Gender.from("  Male  ")).isEqualTo(Gender.MALE);
        }

        @Test
        @DisplayName("enum이라 from()은 새 객체를 만들지 않는다")
        void genderFromReturnsSameInstance() {
            // when & then
            assertThat(Gender.from("male")).isSameAs(Gender.MALE);
        }

        @Test
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void genderRequiredFails() {
            // when & then
            assertThatThrownBy(() -> Gender.from(null))
                    .isInstanceOf(GenderRequiredException.class)
                    .hasMessage("성별은 필수입니다.");

            assertThatThrownBy(() -> Gender.from("   "))
                    .isInstanceOf(GenderRequiredException.class);
        }

        @Test
        @DisplayName("지원하지 않는 값이면 예외가 발생한다")
        void genderUnsupportedFails() {
            // when & then -> valueOf()의 예외가 그대로 새어나가면 500이 된다
            assertThatThrownBy(() -> Gender.from("UNKNOWN"))
                    .isInstanceOf(UnsupportedGenderException.class)
                    .hasMessage("지원하지 않는 성별입니다.");
        }
    }

    @Nested
    @DisplayName("생년월일 테스트")
    class BirthdayTest {

        @Test
        @DisplayName("과거 날짜로 생년월일을 만들 수 있다")
        void createBirthdaySuccess() {
            // given
            LocalDate date = LocalDate.of(1999, 5, 20);

            // when
            Birthday birthday = new Birthday(date);

            // then
            assertThat(birthday.value()).isEqualTo(date);
        }

        @Test
        @DisplayName("오늘과 1900년 1월 1일은 허용된다")
        void birthdayBoundarySuccess() {
            // when & then -> 경계를 잘못 잡으면 신생아와 최고령자가 막힌다
            assertThat(new Birthday(LocalDate.now()).value()).isEqualTo(LocalDate.now());
            assertThat(new Birthday(LocalDate.of(1900, 1, 1)).value())
                    .isEqualTo(LocalDate.of(1900, 1, 1));
        }

        @Test
        @DisplayName("미래 날짜면 예외가 발생한다")
        void birthdayInFutureFails() {
            // when & then
            assertThatThrownBy(() -> new Birthday(LocalDate.now().plusDays(1)))
                    .isInstanceOf(InvalidBirthdayException.class);
        }

        @Test
        @DisplayName("1900년 이전이면 예외가 발생한다")
        void birthdayTooOldFails() {
            // when & then
            assertThatThrownBy(() -> new Birthday(LocalDate.of(1899, 12, 31)))
                    .isInstanceOf(InvalidBirthdayException.class);
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void birthdayRequiredFails() {
            // when & then
            assertThatThrownBy(() -> new Birthday(null))
                    .isInstanceOf(BirthdayRequiredException.class)
                    .hasMessage("생년월일은 필수입니다.");
        }
    }

    @Nested
    @DisplayName("평균 페이스 테스트")
    class AvgPaceTest {

        @Test
        @DisplayName("1km당 소요 시간을 초 단위로 만들 수 있다")
        void createAvgPaceSuccess() {
            // when -> 5'30"/km
            AvgPace pace = new AvgPace(330);

            // then
            assertThat(pace.secondPerKm()).isEqualTo(330);
        }

        @Test
        @DisplayName("120초와 1800초는 허용된다")
        void avgPaceBoundarySuccess() {
            // when & then
            assertThat(new AvgPace(120).secondPerKm()).isEqualTo(120);
            assertThat(new AvgPace(1800).secondPerKm()).isEqualTo(1800);
        }

        @Test
        @DisplayName("허용 범위를 벗어나면 예외가 발생한다")
        void avgPaceOutOfRangeFails() {
            // when & then
            assertThatThrownBy(() -> new AvgPace(119))
                    .isInstanceOf(AvgPaceOutOfRangeException.class)
                    .hasMessage("평균 페이스가 허용 범위를 벗어났습니다.");

            assertThatThrownBy(() -> new AvgPace(1801))
                    .isInstanceOf(AvgPaceOutOfRangeException.class);

            assertThatThrownBy(() -> new AvgPace(0))
                    .isInstanceOf(AvgPaceOutOfRangeException.class);

            assertThatThrownBy(() -> new AvgPace(-1))
                    .isInstanceOf(AvgPaceOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("몸무게 테스트")
    class WeightTest {

        @Test
        @DisplayName("소수 첫째 자리로 반올림된다")
        void weightIsRounded() {
            // when & then -> HALF_UP
            assertThat(new Weight(new BigDecimal("65.55")).value()).isEqualByComparingTo("65.6");
            assertThat(new Weight(new BigDecimal("65.54")).value()).isEqualByComparingTo("65.5");
        }

        @Test
        @DisplayName("20.0kg과 300.0kg은 허용된다")
        void weightBoundarySuccess() {
            // when & then
            assertThat(new Weight(new BigDecimal("20.0")).value()).isEqualByComparingTo("20.0");
            assertThat(new Weight(new BigDecimal("300.0")).value()).isEqualByComparingTo("300.0");
        }

        @Test
        @DisplayName("범위 검사보다 반올림이 먼저 이루어진다")
        void weightIsRoundedBeforeRangeCheck() {
            // when -> 300.04는 300.0으로 정리된 뒤 통과한다
            Weight weight = new Weight(new BigDecimal("300.04"));

            // then -> 저장되는 값이 항상 범위 안임을 보장한다
            assertThat(weight.value()).isEqualByComparingTo("300.0");
        }

        @Test
        @DisplayName("허용 범위를 벗어나면 예외가 발생한다")
        void weightOutOfRangeFails() {
            // when & then
            assertThatThrownBy(() -> new Weight(new BigDecimal("19.9")))
                    .isInstanceOf(WeightOutOfRangeException.class)
                    .hasMessage("몸무게가 허용 범위를 벗어났습니다.");

            assertThatThrownBy(() -> new Weight(new BigDecimal("300.1")))
                    .isInstanceOf(WeightOutOfRangeException.class);
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void weightRequiredFails() {
            // when & then
            assertThatThrownBy(() -> new Weight(null))
                    .isInstanceOf(WeightRequiredException.class)
                    .hasMessage("몸무게는 필수입니다.");
        }
    }

    @Nested
    @DisplayName("키 테스트")
    class HeightTest {

        @Test
        @DisplayName("소수 첫째 자리로 반올림된다")
        void heightIsRounded() {
            // when & then -> HALF_UP
            assertThat(new Height(new BigDecimal("175.45")).value()).isEqualByComparingTo("175.5");
            assertThat(new Height(new BigDecimal("175.44")).value()).isEqualByComparingTo("175.4");
        }

        @Test
        @DisplayName("20.0cm와 300.0cm는 허용된다")
        void heightBoundarySuccess() {
            // when & then
            assertThat(new Height(new BigDecimal("20.0")).value()).isEqualByComparingTo("20.0");
            assertThat(new Height(new BigDecimal("300.0")).value()).isEqualByComparingTo("300.0");
        }

        @Test
        @DisplayName("허용 범위를 벗어나면 예외가 발생한다")
        void heightOutOfRangeFails() {
            // when & then
            assertThatThrownBy(() -> new Height(new BigDecimal("19.9")))
                    .isInstanceOf(HeightOutOfRangeException.class)
                    .hasMessage("키가 허용 범위를 벗어났습니다.");

            assertThatThrownBy(() -> new Height(new BigDecimal("300.1")))
                    .isInstanceOf(HeightOutOfRangeException.class);
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void heightRequiredFails() {
            // when & then -> 몸무게 에러코드가 연결되어 있으면 여기서 잡힌다
            assertThatThrownBy(() -> new Height(null))
                    .isInstanceOf(HeightRequiredException.class)
                    .hasMessage("키는 필수입니다.");
        }
    }
}
