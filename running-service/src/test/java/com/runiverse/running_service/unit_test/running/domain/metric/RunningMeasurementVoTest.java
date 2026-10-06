package com.runiverse.running_service.unit_test.running.domain.metric;

import com.runiverse.running_service.domain.running.metric.exception.CadenceOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.CaloriesOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.ElevationChangeOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.ElevationGainOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.TemperatureOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.TemperatureRequiredException;
import com.runiverse.running_service.domain.running.metric.exception.WeatherCodeOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.Calories;
import com.runiverse.running_service.domain.running.metric.vo.ElevationChange;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.metric.vo.Temperature;
import com.runiverse.running_service.domain.running.metric.vo.WeatherCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningMeasurementVoTest {

    @Nested
    @DisplayName("케이던스 테스트")
    class CadenceTest {

        @ParameterizedTest
        @ValueSource(ints = {1, 160, 180, 300})
        @DisplayName("분당 1보부터 300보까지 만들 수 있다")
        void createCadenceSuccess(int stepsPerMinute) {
            // when & then
            assertThat(new Cadence(stepsPerMinute).stepsPerMinute()).isEqualTo(stepsPerMinute);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 301})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createCadenceOutOfRangeFails(int stepsPerMinute) {
            // when & then -> 0은 멈춰 있는 상태라 케이던스로 저장할 값이 아니다
            assertThatThrownBy(() -> new Cadence(stepsPerMinute))
                    .isInstanceOf(CadenceOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("칼로리 테스트")
    class CaloriesTest {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 350, 20_000})
        @DisplayName("0kcal부터 20000kcal까지 만들 수 있다")
        void createCaloriesSuccess(int kcal) {
            // when & then -> 아주 짧은 구간은 0kcal일 수 있어 0을 허용한다
            assertThat(new Calories(kcal).kcal()).isEqualTo(kcal);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 20_001})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createCaloriesOutOfRangeFails(int kcal) {
            // when & then
            assertThatThrownBy(() -> new Calories(kcal))
                    .isInstanceOf(CaloriesOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("날씨 코드 테스트")
    class WeatherCodeTest {

        @ParameterizedTest
        @ValueSource(ints = {0, 61, 99})
        @DisplayName("WMO 4677 범위인 0부터 99까지 만들 수 있다")
        void createWeatherCodeSuccess(int value) {
            // when & then -> 0(맑음)도 유효한 코드라 0을 허용해야 한다
            assertThat(new WeatherCode(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 100})
        @DisplayName("코드표 범위를 벗어나면 예외가 발생한다")
        void createWeatherCodeOutOfRangeFails(int value) {
            // when & then
            assertThatThrownBy(() -> new WeatherCode(value))
                    .isInstanceOf(WeatherCodeOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("기온 테스트")
    class TemperatureTest {

        @ParameterizedTest
        @ValueSource(strings = {"-99.9", "-15.5", "0.0", "23.4", "99.9"})
        @DisplayName("영하를 포함해 -99.9도부터 99.9도까지 만들 수 있다")
        void createTemperatureSuccess(String celsius) {
            // when & then -> 영하가 막히면 겨울 러닝이 저장되지 않는다
            assertThat(new Temperature(new BigDecimal(celsius)).celsius())
                    .isEqualByComparingTo(celsius);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-100.0", "100.0", "-200", "150.5"})
        @DisplayName("numeric(3,1)이 담을 수 없는 값이면 예외가 발생한다")
        void createTemperatureOutOfRangeFails(String celsius) {
            // when & then
            assertThatThrownBy(() -> new Temperature(new BigDecimal(celsius)))
                    .isInstanceOf(TemperatureOutOfRangeException.class);
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void createTemperatureWithNullFails() {
            // when & then
            assertThatThrownBy(() -> new Temperature(null))
                    .isInstanceOf(TemperatureRequiredException.class);
        }

        @ParameterizedTest
        @CsvSource({"23.44, 23.4", "23.45, 23.5", "23.46, 23.5", "-0.05, -0.1"})
        @DisplayName("소수점 둘째 자리에서 반올림된다")
        void temperatureIsRoundedToOneDecimal(String given, String expected) {
            // when
            Temperature temperature = new Temperature(new BigDecimal(given));

            // then -> DB가 numeric(3,1)이라 저장 전에 자리수를 맞춘다
            assertThat(temperature.celsius()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("반올림 후 범위를 벗어나면 예외가 발생한다")
        void temperatureRoundedBeyondMaxFails() {
            // when & then -> 99.94는 99.9로 내려와 통과하지만 99.95는 100.0이 되어 막힌다
            assertThat(new Temperature(new BigDecimal("99.94")).celsius())
                    .isEqualByComparingTo("99.9");
            assertThatThrownBy(() -> new Temperature(new BigDecimal("99.95")))
                    .isInstanceOf(TemperatureOutOfRangeException.class);
        }

        @Test
        @DisplayName("자리수가 달라도 같은 기온이면 같은 값으로 취급한다")
        void temperatureIsValueBasedAfterScaling() {
            // when & then -> setScale이 없으면 BigDecimal 동등성이 scale까지 따져 15와 15.0이 달라진다
            assertThat(new Temperature(new BigDecimal("15")))
                    .isEqualTo(new Temperature(new BigDecimal("15.00")));
        }
    }

    @Nested
    @DisplayName("누적 상승 고도 테스트")
    class ElevationGainTest {

        @ParameterizedTest
        @ValueSource(ints = {0, 120, 20_000})
        @DisplayName("0m부터 20000m까지 만들 수 있다")
        void createElevationGainSuccess(int meters) {
            // when & then -> 평지 러닝은 0m다
            assertThat(new ElevationGain(meters).meters()).isEqualTo(meters);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -120, 20_001})
        @DisplayName("음수이거나 상한을 넘으면 예외가 발생한다")
        void createElevationGainOutOfRangeFails(int meters) {
            // when & then -> 누적 상승은 오르막 상승분만 더한 값이라 음수가 될 수 없다
            assertThatThrownBy(() -> new ElevationGain(meters))
                    .isInstanceOf(ElevationGainOutOfRangeException.class);
        }
    }

    @Nested
    @DisplayName("고도 순변화 테스트")
    class ElevationChangeTest {

        @ParameterizedTest
        @ValueSource(ints = {-10_000, -35, 0, 35, 10_000})
        @DisplayName("내리막 구간의 음수까지 만들 수 있다")
        void createElevationChangeSuccess(int meters) {
            // when & then -> 구간 순변화는 시작·끝 고도 차라 내리막이면 음수다
            assertThat(new ElevationChange(meters).meters()).isEqualTo(meters);
        }

        @ParameterizedTest
        @ValueSource(ints = {-10_001, 10_001})
        @DisplayName("범위를 벗어나면 예외가 발생한다")
        void createElevationChangeOutOfRangeFails(int meters) {
            // when & then
            assertThatThrownBy(() -> new ElevationChange(meters))
                    .isInstanceOf(ElevationChangeOutOfRangeException.class);
        }

        @Test
        @DisplayName("누적 상승 고도와 달리 음수를 받는다")
        void elevationChangeAcceptsWhatGainRejects() {
            // when & then -> 두 VO를 하나로 합치면 이 차이가 사라진다
            assertThat(new ElevationChange(-50).meters()).isEqualTo(-50);
            assertThatThrownBy(() -> new ElevationGain(-50))
                    .isInstanceOf(ElevationGainOutOfRangeException.class);
        }
    }
}
