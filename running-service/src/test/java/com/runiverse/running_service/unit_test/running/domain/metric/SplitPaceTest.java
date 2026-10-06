package com.runiverse.running_service.unit_test.running.domain.metric;

import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;
import com.runiverse.running_service.domain.running.metric.exception.PaceOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.SplitPaceOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.metric.vo.SplitPace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("구간 페이스 VO 단위 테스트")
public class SplitPaceTest {

    // 10m 구간의 페이스 = 소요 초 × 100
    private static int paceOf10mSplit(int durationSeconds) {
        return durationSeconds * 100;
    }

    @Nested
    @DisplayName("짧은 구간에서 실제로 나오는 값들을 받아들인다")
    class AcceptsShortSplitReality {

        @Test
        @DisplayName("신호 대기로 10m 구간이 40초 걸려도 통과한다")
        void acceptsSlowSplitFromTrafficLight() {
            // given -> Pace(MAX 3600)였다면 여기서 기록 전체가 사라진다
            int pace = paceOf10mSplit(40);   // 4000 초/km

            // when & then
            assertThatCode(() -> new SplitPace(pace)).doesNotThrowAnyException();
            assertThatThrownBy(() -> new Pace(pace))
                    .isInstanceOf(PaceOutOfRangeException.class);
        }

        @Test
        @DisplayName("GPS가 튀어 10m 구간이 1초로 잡혀도 통과한다")
        void acceptsFastSplitFromGpsJump() {
            // given -> 10m/s = 36km/h. 사람이 낼 수 없지만 GPS 튐으로는 나온다
            int pace = paceOf10mSplit(1);    // 100 초/km

            // when & then
            assertThatCode(() -> new SplitPace(pace)).doesNotThrowAnyException();
            assertThatThrownBy(() -> new Pace(pace))
                    .isInstanceOf(PaceOutOfRangeException.class);
        }

        @Test
        @DisplayName("10분 넘게 멈춰 있던 구간도 통과한다")
        void acceptsLongStop() {
            // when & then -> 일시정지가 아직 구현 전이라 긴 정지가 그대로 구간에 들어온다
            assertThatCode(() -> new SplitPace(paceOf10mSplit(700)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("구조적 범위는 지킨다")
    class KeepsStructuralBounds {

        @Test
        @DisplayName("0 이하는 거부한다")
        void rejectsZeroOrNegative() {
            // when & then -> 시간이 0인 구간은 ElapsedTime이 먼저 막지만 여기서도 닫아둔다
            assertThatThrownBy(() -> new SplitPace(0))
                    .isInstanceOf(SplitPaceOutOfRangeException.class);
            assertThatThrownBy(() -> new SplitPace(-1))
                    .isInstanceOf(SplitPaceOutOfRangeException.class);
        }

        @Test
        @DisplayName("경계값을 받아들인다")
        void acceptsBoundaries() {
            // when & then
            assertThatCode(() -> new SplitPace(1)).doesNotThrowAnyException();
            assertThatCode(() -> new SplitPace(86_400_000)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("구조적 상한을 넘으면 거부한다")
        void rejectsAboveStructuralMax() {
            // when & then
            assertThatThrownBy(() -> new SplitPace(86_400_001))
                    .isInstanceOf(SplitPaceOutOfRangeException.class);
        }

        @Test
        @DisplayName("전용 에러 코드를 단다")
        void carriesOwnErrorCode() {
            // when & then -> Pace와 섞이면 원인을 못 찾는다
            assertThatThrownBy(() -> new SplitPace(0))
                    .isInstanceOf(SplitPaceOutOfRangeException.class)
                    .extracting(e -> ((SplitPaceOutOfRangeException) e).getErrorCode())
                    .isEqualTo(RunningMetricErrorCode.SPLIT_PACE_OUT_OF_RANGE);
        }
    }

    @Test
    @DisplayName("기록 전체 평균은 여전히 사람이 낼 수 있는 범위로 막힌다")
    void wholeRecordPaceKeepsItsGuard() {
        // given & then -> 구간 검증을 푼 대신 이쪽이 데이터 오류를 막는다
        assertThatThrownBy(() -> new Pace(119)).isInstanceOf(PaceOutOfRangeException.class);
        assertThatThrownBy(() -> new Pace(3_601)).isInstanceOf(PaceOutOfRangeException.class);
        assertThat(new Pace(345).secondsPerKm()).isEqualTo(345);
    }
}
