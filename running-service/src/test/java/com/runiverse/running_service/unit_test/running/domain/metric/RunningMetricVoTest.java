package com.runiverse.running_service.unit_test.running.domain.metric;

import com.runiverse.running_service.domain.running.metric.exception.DistanceOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.ElapsedTimeOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.InvalidRunningPeriodException;
import com.runiverse.running_service.domain.running.metric.exception.PaceOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.RunningPeriodRequiredException;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.ElapsedTime;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.metric.vo.RunningPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningMetricVoTest {

    @Nested
    @DisplayName("거리 테스트")
    class DistanceTest {

        @ParameterizedTest
        @ValueSource(ints = {1, 5_000, 42_195, 500_000})
        @DisplayName("1m부터 500km까지 만들 수 있다")
        void createDistanceSuccess(int meters) {
            // when & then -> 경계를 잘못 잡으면 정상 기록이 저장 단계에서 막힌다
            assertThat(new Distance(meters).meters()).isEqualTo(meters);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 500_001, Integer.MAX_VALUE})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createDistanceOutOfRangeFails(int meters) {
            // when & then -> 0m 기록과 GPS 튐으로 부풀려진 거리를 막는다
            assertThatThrownBy(() -> new Distance(meters))
                    .isInstanceOf(DistanceOutOfRangeException.class);
        }

        @Test
        @DisplayName("같은 거리면 같은 값으로 취급한다")
        void distanceIsValueBased() {
            // when & then
            assertThat(new Distance(5_000)).isEqualTo(new Distance(5_000));
        }

        @Test
        @DisplayName("목표 없는 솔로는 도달 불가능한 상한으로 표현한다")
        void unlimitedIsUpperBound() {
            // when & then -> running_players.target_distance가 NOT NULL이라 값은 넣어야 하는데,
            //                끝은 유저가 정하므로 상한을 목표로 세운다
            assertThat(Distance.unlimited().meters()).isEqualTo(500_000);
            assertThat(Distance.unlimited().isUnlimited()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5_000, 499_999})
        @DisplayName("상한 미만은 목표가 정해진 거리다")
        void boundedDistanceIsNotUnlimited(int meters) {
            // when & then -> 500000을 "500km 목표"로 오해하지 않게 읽는 쪽이 구분한다
            assertThat(new Distance(meters).isUnlimited()).isFalse();
        }
    }

    @Nested
    @DisplayName("페이스 테스트")
    class PaceTest {

        @ParameterizedTest
        @ValueSource(ints = {120, 330, 600, 3600})
        @DisplayName("1km당 120초부터 3600초까지 만들 수 있다")
        void createPaceSuccess(int secondsPerKm) {
            // when & then
            assertThat(new Pace(secondsPerKm).secondsPerKm()).isEqualTo(secondsPerKm);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 119, 3601})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createPaceOutOfRangeFails(int secondsPerKm) {
            // when & then -> 사람이 낼 수 없는 페이스와 멈춰 선 구간을 막는다
            assertThatThrownBy(() -> new Pace(secondsPerKm))
                    .isInstanceOf(PaceOutOfRangeException.class);
        }

        @Test
        @DisplayName("온보딩 희망 페이스보다 느린 값도 허용한다")
        void paceAllowsSlowerThanOnboardingRange() {
            // when & then -> 실측은 신호 대기·걷기가 섞여 온보딩 상한(1800)을 넘을 수 있다
            assertThat(new Pace(1_801).secondsPerKm()).isEqualTo(1_801);
        }
    }

    @Nested
    @DisplayName("소요 시간 테스트")
    class ElapsedTimeTest {

        @ParameterizedTest
        @ValueSource(ints = {1, 1_800, 86_400})
        @DisplayName("1초부터 24시간까지 만들 수 있다")
        void createElapsedTimeSuccess(int seconds) {
            // when & then
            assertThat(new ElapsedTime(seconds).seconds()).isEqualTo(seconds);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 86_401})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createElapsedTimeOutOfRangeFails(int seconds) {
            // when & then -> 24시간을 넘는 세션은 종료 신호를 놓친 상태로 본다
            assertThatThrownBy(() -> new ElapsedTime(seconds))
                    .isInstanceOf(ElapsedTimeOutOfRangeException.class);
        }

        @Test
        @DisplayName("구간 시간을 더할 수 있다")
        void plusAddsSeconds() {
            // given
            ElapsedTime first = new ElapsedTime(300);
            ElapsedTime second = new ElapsedTime(270);

            // when
            ElapsedTime total = first.plus(second);

            // then
            assertThat(total.seconds()).isEqualTo(570);
        }

        @Test
        @DisplayName("더한 결과가 상한을 넘으면 예외가 발생한다")
        void plusBeyondMaxFails() {
            // given
            ElapsedTime almostMax = new ElapsedTime(86_400);

            // when & then -> 합산 결과도 같은 불변식을 지켜야 한다
            assertThatThrownBy(() -> almostMax.plus(new ElapsedTime(1)))
                    .isInstanceOf(ElapsedTimeOutOfRangeException.class);
        }

        @Test
        @DisplayName("더하기는 원본을 바꾸지 않는다")
        void plusDoesNotMutate() {
            // given
            ElapsedTime original = new ElapsedTime(300);

            // when
            original.plus(new ElapsedTime(100));

            // then
            assertThat(original.seconds()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("러닝 구간 시각 테스트")
    class RunningPeriodTest {

        private static final LocalDateTime START = LocalDateTime.of(2026, 8, 19, 6, 0);

        @Test
        @DisplayName("종료가 시작보다 뒤면 만들 수 있다")
        void createRunningPeriodSuccess() {
            // when
            RunningPeriod period = new RunningPeriod(START, START.plusMinutes(30));

            // then
            assertThat(period.startAt()).isEqualTo(START);
            assertThat(period.endAt()).isEqualTo(START.plusMinutes(30));
        }

        @Test
        @DisplayName("1초 차이도 허용된다")
        void createRunningPeriodWithOneSecondSuccess() {
            // when & then -> 경계를 잘못 잡으면 아주 짧은 구간이 막힌다
            assertThat(new RunningPeriod(START, START.plusSeconds(1)).endAt())
                    .isEqualTo(START.plusSeconds(1));
        }

        @Test
        @DisplayName("시작과 종료가 같으면 예외가 발생한다")
        void samePeriodFails() {
            // when & then
            assertThatThrownBy(() -> new RunningPeriod(START, START))
                    .isInstanceOf(InvalidRunningPeriodException.class);
        }

        @Test
        @DisplayName("종료가 시작보다 앞이면 예외가 발생한다")
        void reversedPeriodFails() {
            // when & then -> 뒤집힌 구간이 통과하면 소요 시간이 음수가 된다
            assertThatThrownBy(() -> new RunningPeriod(START, START.minusSeconds(1)))
                    .isInstanceOf(InvalidRunningPeriodException.class);
        }

        @Test
        @DisplayName("시작이나 종료가 null이면 예외가 발생한다")
        void nullPeriodFails() {
            // when & then
            assertThatThrownBy(() -> new RunningPeriod(null, START))
                    .isInstanceOf(RunningPeriodRequiredException.class);
            assertThatThrownBy(() -> new RunningPeriod(START, null))
                    .isInstanceOf(RunningPeriodRequiredException.class);
            assertThatThrownBy(() -> new RunningPeriod(null, null))
                    .isInstanceOf(RunningPeriodRequiredException.class);
        }
    }
}
