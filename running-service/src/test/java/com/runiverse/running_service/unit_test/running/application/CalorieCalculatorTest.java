package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.CalorieCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("칼로리 계산 단위 테스트")
public class CalorieCalculatorTest {

    private static final BigDecimal WEIGHT_70KG = new BigDecimal("70.0");
    private static final int PACE_10KMH = 360;   // 초/km
    private static final int ONE_HOUR = 3_600;

    @Test
    @DisplayName("70kg이 10km/h로 한 시간 뛰면 실측 범위에 든다")
    void matchesRealWorldRangeForOneHourRun() {
        // when
        int kcal = CalorieCalculator.kcal(PACE_10KMH, ONE_HOUR, WEIGHT_70KG);

        // then -> 공식이나 계수를 잘못 넣으면 여기서 크게 벌어진다
        assertThat(kcal).isBetween(700, 780);
    }

    @Test
    @DisplayName("5km를 30분에 뛰면 약 370kcal이다")
    void matchesRealWorldRangeForFiveKilometers() {
        // when & then -> 실측 통용값은 350~400 사이다
        assertThat(CalorieCalculator.kcal(PACE_10KMH, 1_800, WEIGHT_70KG))
                .isBetween(350, 400);
    }

    @Test
    @DisplayName("멈춰 있던 구간은 안정 대사량만큼만 계산한다")
    void stoppedSegmentCostsRestingMetabolismOnly() {
        // given -> 10m를 700초에 지난 구간(사실상 정지). 페이스는 70,000 초/km
        int kcal = CalorieCalculator.kcal(70_000, 700, WEIGHT_70KG);

        // then -> MET가 1로 수렴하므로 70kg × 700/3600시간 ≈ 14kcal.
        // clamp를 걸었다면 8km/h로 달린 것처럼 부풀려진다
        assertThat(kcal).isCloseTo(14, within(1));
    }

    @Test
    @DisplayName("같은 조건이면 체중에 비례한다")
    void scalesWithWeight() {
        // when
        int light = CalorieCalculator.kcal(PACE_10KMH, ONE_HOUR, new BigDecimal("50.0"));
        int heavy = CalorieCalculator.kcal(PACE_10KMH, ONE_HOUR, new BigDecimal("100.0"));

        // then
        assertThat(heavy).isCloseTo(light * 2, within(2));
    }

    @Test
    @DisplayName("같은 조건이면 시간에 비례한다")
    void scalesWithDuration() {
        // when
        int half = CalorieCalculator.kcal(PACE_10KMH, 1_800, WEIGHT_70KG);
        int full = CalorieCalculator.kcal(PACE_10KMH, ONE_HOUR, WEIGHT_70KG);

        // then
        assertThat(full).isCloseTo(half * 2, within(2));
    }

    @Test
    @DisplayName("빠를수록 같은 시간에 더 쓴다")
    void fasterPaceBurnsMoreInTheSameTime() {
        // given -> 8km/h, 10km/h, 14km/h를 같은 시간 동안
        int slow = CalorieCalculator.kcal(450, ONE_HOUR, WEIGHT_70KG);
        int medium = CalorieCalculator.kcal(PACE_10KMH, ONE_HOUR, WEIGHT_70KG);
        int fast = CalorieCalculator.kcal(257, ONE_HOUR, WEIGHT_70KG);

        // then
        assertThat(slow).isLessThan(medium);
        assertThat(medium).isLessThan(fast);
    }

    @Test
    @DisplayName("10m 구간처럼 짧아도 음수가 나오지 않는다")
    void shortSplitNeverGoesNegative() {
        // given -> 10m를 3초에 지난 구간(페이스 300 초/km)
        int kcal = CalorieCalculator.kcal(300, 3, WEIGHT_70KG);

        // then -> Calories VO가 0 이상만 받는다
        assertThat(kcal).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("구조적 상한 페이스에서도 계산이 무너지지 않는다")
    void handlesStructuralMaxPace() {
        // given -> SplitPace 상한(86,400,000 초/km)
        int kcal = CalorieCalculator.kcal(86_400_000, 60, WEIGHT_70KG);

        // then -> 속도가 사실상 0이라 MET 1, 70kg × 60/3600시간 ≈ 1kcal
        assertThat(kcal).isCloseTo(1, within(1));
    }
}
