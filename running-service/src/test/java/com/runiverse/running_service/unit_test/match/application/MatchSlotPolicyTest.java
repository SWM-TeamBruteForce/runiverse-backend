package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.common.exception.MatchErrorCode;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 신청 검증과 슬롯 조회가 이 판정 하나를 함께 쓴다 — 여기가 어긋나면 고를 수 있는 슬롯이 신청에서 거절된다
@DisplayName("매칭 신청 선택지 정책 단위 테스트")
class MatchSlotPolicyTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);
    // api-spec.md 매칭 신청 400 문구 — 운영 설정이면 이 문장 그대로 나가야 클라 계약이 유지된다
    private static final String START_TIME_MESSAGE = "시작 시각은 18:00부터 22:00까지 30분 간격으로만 선택할 수 있습니다.";
    private static final String DISTANCE_MESSAGE = "목표 거리는 3000, 5000, 10000 중 하나여야 합니다.";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Nested
    @DisplayName("운영 설정(18:00~22:00, 30분, 3·5·10km)")
    class OperatingPolicy {

        private final MatchSlotPolicy policy = policyOf(
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));

        @Test
        @DisplayName("그날의 슬롯을 첫 시각부터 마지막 시각까지 오름차순으로 만든다")
        void listsStartTimesInAscendingOrder() {
            // when
            List<LocalDateTime> startTimes = policy.startTimesOf(DATE);

            // then -> 양 끝을 포함한 9개
            assertThat(startTimes).containsExactly(
                    DATE.atTime(18, 0), DATE.atTime(18, 30), DATE.atTime(19, 0),
                    DATE.atTime(19, 30), DATE.atTime(20, 0), DATE.atTime(20, 30),
                    DATE.atTime(21, 0), DATE.atTime(21, 30), DATE.atTime(22, 0));
        }

        @Test
        @DisplayName("만든 슬롯은 모두 신청 검증을 통과한다")
        void everyListedSlotIsAllowed() {
            // when & then -> 조회가 보여 준 슬롯을 신청이 거절하면 안 된다
            assertThat(policy.startTimesOf(DATE)).allMatch(policy::allowsStartAt);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"18:00", "18:30", "19:00", "21:30", "22:00"})
        @DisplayName("범위 안의 간격 시각은 받는다")
        void allowsAlignedTimeInRange(String time) {
            // when & then -> 양 끝 경계도 선택지에 들어간다
            assertThat(policy.allowsStartAt(DATE.atTime(LocalTime.parse(time)))).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"00:00", "17:30", "17:59", "22:01", "22:30", "23:59"})
        @DisplayName("범위 밖 시각은 거절한다")
        void rejectsOutOfRange(String time) {
            // when & then
            assertThat(policy.allowsStartAt(DATE.atTime(LocalTime.parse(time)))).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"18:01", "19:15", "21:45"})
        @DisplayName("범위 안이어도 간격에 맞지 않으면 거절한다")
        void rejectsUnalignedTime(String time) {
            // when & then
            assertThat(policy.allowsStartAt(DATE.atTime(LocalTime.parse(time)))).isFalse();
        }

        @Test
        @DisplayName("초·나노가 붙으면 거절한다")
        void rejectsSecondsAndNanos() {
            // given -> 분만 보면 통과하지만 슬롯이 아니다
            LocalDateTime withSeconds = DATE.atTime(19, 0, 30);
            LocalDateTime withNanos = DATE.atTime(19, 0, 0, 1);

            // when & then
            assertThat(policy.allowsStartAt(withSeconds)).isFalse();
            assertThat(policy.allowsStartAt(withNanos)).isFalse();
        }

        @ParameterizedTest(name = "{0}m")
        @ValueSource(ints = {3_000, 5_000, 10_000})
        @DisplayName("허용 거리는 받는다")
        void allowsListedDistance(int distance) {
            // when & then
            assertThat(policy.allowsDistance(distance)).isTrue();
        }

        @ParameterizedTest(name = "{0}m")
        @ValueSource(ints = {0, -5_000, 1_000, 4_000, 7_000, 20_000})
        @DisplayName("목록에 없는 거리는 거절한다")
        void rejectsUnlistedDistance(int distance) {
            // when & then
            assertThat(policy.allowsDistance(distance)).isFalse();
        }

        @Test
        @DisplayName("선택지 안의 시각·거리면 신청 검증을 통과한다")
        void validatePassesAllowedOption() {
            // when & then
            assertThatCode(() -> policy.validate(DATE.atTime(19, 0), 5_000)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("시각만 어긋나면 시각 문구 하나로 거절한다")
        void validateRejectsStartTimeOnly() {
            // when & then -> 운영 설정이면 명세 문구와 글자까지 같다
            assertThatThrownBy(() -> policy.validate(DATE.atTime(19, 15), 5_000))
                    .isInstanceOf(InvalidMatchOptionException.class)
                    .hasMessage(START_TIME_MESSAGE)
                    .extracting(e -> ((InvalidMatchOptionException) e).getErrorCode())
                    .isEqualTo(MatchErrorCode.INVALID_MATCH_OPTION);
        }

        @Test
        @DisplayName("거리만 어긋나면 거리 문구 하나로 거절한다")
        void validateRejectsDistanceOnly() {
            // when & then
            assertThatThrownBy(() -> policy.validate(DATE.atTime(19, 0), 7_000))
                    .isInstanceOf(InvalidMatchOptionException.class)
                    .hasMessage(DISTANCE_MESSAGE);
        }

        @Test
        @DisplayName("마감(시작 10분 전) 1초 전까지는 열려 있다")
        void openUntilJustBeforeClose() {
            // when & then
            assertThat(policy.isOpen(DATE.atTime(19, 0), DATE.atTime(18, 49, 59))).isTrue();
        }

        @Test
        @DisplayName("마감 정각은 이미 마감이다")
        void closedExactlyAtCloseTime() {
            // when & then -> 그 시점에 확정 판정이 돈다. 신청 409와 조회 selectable이 이 경계를 함께 쓴다
            assertThat(policy.isOpen(DATE.atTime(19, 0), DATE.atTime(18, 50))).isFalse();
        }

        @Test
        @DisplayName("마감이 지나면 닫혀 있다")
        void closedAfterCloseTime() {
            // when & then
            assertThat(policy.isOpen(DATE.atTime(19, 0), DATE.atTime(18, 50, 1))).isFalse();
            assertThat(policy.isOpen(DATE.atTime(19, 0), DATE.atTime(19, 30))).isFalse();
        }

        @Test
        @DisplayName("슬롯 조회는 허용 거리면 통과한다")
        void validateDistancePassesListedDistance() {
            // when & then
            assertThatCode(() -> policy.validateDistance(10_000)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("슬롯 조회는 목록 밖 거리를 신청과 같은 문구로 거절한다")
        void validateDistanceRejectsWithSameMessage() {
            // when & then -> 두 API의 거리 안내가 갈라지지 않는다
            assertThatThrownBy(() -> policy.validateDistance(7_000))
                    .isInstanceOf(InvalidMatchOptionException.class)
                    .hasMessage(DISTANCE_MESSAGE);
        }

        @Test
        @DisplayName("둘 다 어긋나면 시각·거리 순으로 두 문장을 공백으로 이어 한 번에 거절한다")
        void validateRejectsBothAtOnce() {
            // when & then -> 하나 고치고 다시 보내야 나머지를 알게 하지 않는다
            assertThatThrownBy(() -> policy.validate(DATE.atTime(23, 0), 7_000))
                    .isInstanceOf(InvalidMatchOptionException.class)
                    .hasMessage(START_TIME_MESSAGE + " " + DISTANCE_MESSAGE);
        }
    }

    @Nested
    @DisplayName("설정이 바뀌면")
    class ChangedPolicy {

        @Test
        @DisplayName("슬롯과 신청 검증이 새 값을 함께 따른다")
        void followsChangedSettings() {
            // given -> 정책은 코드가 아니라 설정이다
            MatchSlotPolicy policy = policyOf(
                    LocalTime.of(6, 0), LocalTime.of(7, 0), Duration.ofMinutes(20), List.of(7_000));

            // when & then
            assertThat(policy.startTimesOf(DATE)).containsExactly(
                    DATE.atTime(6, 0), DATE.atTime(6, 20), DATE.atTime(6, 40), DATE.atTime(7, 0));
            assertThat(policy.allowsStartAt(DATE.atTime(6, 20))).isTrue();
            assertThat(policy.allowsStartAt(DATE.atTime(6, 30))).isFalse();
            assertThat(policy.allowsStartAt(DATE.atTime(19, 0))).isFalse();
            assertThat(policy.allowsDistance(7_000)).isTrue();
            assertThat(policy.allowsDistance(5_000)).isFalse();
        }

        @Test
        @DisplayName("거절 문구도 새 값을 따르고 거리는 설정 순서대로 나간다")
        void messageFollowsChangedSettings() {
            // given -> 설정만 바꾸고 안내가 옛 값을 말하면 안 된다. 거리는 일부러 정렬하지 않은 순서로 둔다
            MatchSlotPolicy policy = policyOf(
                    LocalTime.of(6, 0), LocalTime.of(9, 0), Duration.ofMinutes(90), List.of(10_000, 3_000));

            // when & then
            assertThatThrownBy(() -> policy.validate(DATE.atTime(7, 0), 5_000))
                    .isInstanceOf(InvalidMatchOptionException.class)
                    .hasMessage("시작 시각은 06:00부터 09:00까지 90분 간격으로만 선택할 수 있습니다."
                            + " 목표 거리는 10000, 3000 중 하나여야 합니다.");
        }

        @Test
        @DisplayName("첫 시각과 마지막 시각이 같으면 슬롯은 하나다")
        void singleSlotWhenFirstEqualsLast() {
            // given
            MatchSlotPolicy policy = policyOf(
                    LocalTime.of(19, 0), LocalTime.of(19, 0), Duration.ofMinutes(30), List.of(5_000));

            // when & then
            assertThat(policy.startTimesOf(DATE)).containsExactly(DATE.atTime(19, 0));
        }
    }

    @Nested
    @DisplayName("설정 검증")
    class SettingsValidation {

        @Test
        @DisplayName("운영 설정은 통과한다")
        void operatingSettingsPass() {
            // when & then
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000))).isEmpty();
        }

        @Test
        @DisplayName("첫 시각이 마지막 시각보다 늦으면 거절한다")
        void rejectsReversedRange() {
            // when & then
            assertThat(validate(LocalTime.of(22, 0), LocalTime.of(18, 0),
                    Duration.ofMinutes(30), List.of(5_000)))
                    .containsExactly("첫 시작 시각은 마지막 시작 시각보다 늦을 수 없다");
        }

        @Test
        @DisplayName("간격이 0이면 거절한다")
        void rejectsZeroInterval() {
            // when & then -> 0으로 나누거나 슬롯을 끝없이 만들기 전에 기동에서 막는다
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ZERO, List.of(5_000)))
                    .containsExactly("시작 시각 간격은 1분 이상, 분 단위여야 한다");
        }

        @Test
        @DisplayName("간격이 음수면 거절한다")
        void rejectsNegativeInterval() {
            // when & then
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ofMinutes(-30), List.of(5_000)))
                    .containsExactly("시작 시각 간격은 1분 이상, 분 단위여야 한다");
        }

        @Test
        @DisplayName("간격이 분 단위가 아니면 거절한다")
        void rejectsIntervalWithSeconds() {
            // when & then -> 90초는 나누어떨어져도 "N분 간격" 문구를 만들 수 없다
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(18, 3),
                    Duration.ofSeconds(90), List.of(5_000)))
                    .containsExactly("시작 시각 간격은 1분 이상, 분 단위여야 한다");
        }

        @Test
        @DisplayName("마지막 시각이 간격에 맞지 않으면 거절한다")
        void rejectsLastTimeOffInterval() {
            // when & then -> 45분 간격이면 22:00은 슬롯이 아니다
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ofMinutes(45), List.of(5_000)))
                    .containsExactly("첫·마지막 시작 시각의 차이는 간격으로 나누어떨어져야 한다");
        }

        @Test
        @DisplayName("허용 거리가 비거나 양수가 아니면 거절한다")
        void rejectsEmptyOrNonPositiveDistances() {
            // when & then
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ofMinutes(30), List.of())).hasSize(1);
            assertThat(validate(LocalTime.of(18, 0), LocalTime.of(22, 0),
                    Duration.ofMinutes(30), List.of(5_000, 0))).hasSize(1);
        }

        @Test
        @DisplayName("값이 비면 필수 위반만 낸다")
        void nullReportsOnlyRequired() {
            // when & then -> 순서·간격 검사가 null에 또 걸리면 원인이 가려진다
            assertThat(validate(null, null, null, List.of(5_000))).hasSize(3);
        }
    }

    private static MatchSlotPolicy policyOf(LocalTime first, LocalTime last, Duration interval,
                                            List<Integer> distances) {
        return new MatchSlotPolicy(properties(first, last, interval, distances));
    }

    private static List<String> validate(LocalTime first, LocalTime last, Duration interval,
                                         List<Integer> distances) {
        Set<ConstraintViolation<MatchProperties>> violations =
                validator.validate(properties(first, last, interval, distances));
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }

    // 슬롯과 무관한 값은 운영 설정 그대로 둔다
    private static MatchProperties properties(LocalTime first, LocalTime last, Duration interval,
                                              List<Integer> distances) {
        return new MatchProperties(Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6),
                10, Duration.ofMinutes(20), first, last, interval, distances);
    }
}
