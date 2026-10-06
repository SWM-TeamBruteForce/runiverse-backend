package com.runiverse.running_service.unit_test.running.domain.record;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.SplitDraft;
import com.runiverse.running_service.domain.running.metric.exception.CaloriesRequiredException;
import com.runiverse.running_service.domain.running.record.exception.SplitNumberNotSequentialException;
import com.runiverse.running_service.domain.running.record.exception.SplitPeriodNotSequentialException;
import com.runiverse.running_service.domain.running.record.exception.SplitPeriodOutOfRecordException;
import com.runiverse.running_service.domain.running.record.exception.SplitRouteNotConnectedException;
import com.runiverse.running_service.domain.running.record.exception.SplitRouteNotStartingAtOriginException;
import com.runiverse.running_service.domain.running.record.exception.SplitsRequiredException;
import com.runiverse.running_service.domain.running.metric.exception.TemperatureRequiredException;
import com.runiverse.running_service.domain.running.metric.exception.WeatherCodeRequiredException;
import com.runiverse.running_service.domain.running.record.vo.RunningRecordId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRecordTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 19, 6, 0);
    private static final LocalDateTime END = START.plusMinutes(30);

    // 3km / 3구간짜리 정상 기록 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningRecord.RunningRecordBuilder validRecord() {
        return RunningRecord.finish()
                .runningRoomId(125L)
                .userId(UuidCreator.getTimeOrderedEpoch())
                .avgPace(330)
                .totalDistance(3_000)
                .totalDuration(990)
                .totalCalories(210)
                .gpsTrackKey("gps-tracks/2026/08/19/125-abc.json")
                .routePolyline("_p~iF~ps|U_ulLnnqC")
                .startAt(START)
                .endAt(END)
                .weatherCode(61)
                .temperature(new BigDecimal("23.4"))
                .splits(validSplits());
    }

    private static List<SplitDraft> validSplits() {
        return List.of(
                split(1, 0, 120, START, START.plusMinutes(5)),
                split(2, 120, 245, START.plusMinutes(5), START.plusMinutes(11)),
                split(3, 245, 380, START.plusMinutes(11), START.plusMinutes(16)));
    }

    // 구간은 기록을 거쳐야만 만들어진다 — 테스트도 원시 입력까지만 채운다
    private static SplitDraft split(int splitNumber, int routeStartIndex, int routeEndIndex,
                                    LocalDateTime startAt, LocalDateTime endAt) {
        return SplitDraft.create(splitNumber, 330, 1_000, 330,
                routeStartIndex, routeEndIndex, startAt, endAt, 70, null, null);
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("정상 값으로 러닝 기록을 만들 수 있다")
        void createRunningRecordSuccess() {
            // when
            RunningRecord record = validRecord().build();

            // then
            assertThat(record.getTotalDistance().meters()).isEqualTo(3_000);
            assertThat(record.getTotalCalories().kcal()).isEqualTo(210);
            assertThat(record.getWeatherCode().value()).isEqualTo(61);
            assertThat(record.getTemperature().celsius()).isEqualByComparingTo("23.4");
            assertThat(record.getSplits()).hasSize(3);
        }

        @Test
        @DisplayName("구간이 하나뿐인 기록도 만들 수 있다")
        void createRecordWithSingleSplit() {
            // when & then -> 1km 미만 러닝은 구간이 하나다
            RunningRecord record = validRecord()
                    .splits(List.of(split(1, 0, 40, START, END)))
                    .build();

            assertThat(record.getSplits()).hasSize(1);
        }

        @Test
        @DisplayName("선택 항목은 비워둘 수 있다")
        void optionalMetricsCanBeEmpty() {
            // when
            RunningRecord record = validRecord().build();

            // then -> 센서가 없으면 케이던스·고도가 없다
            assertThat(record.getAvgCadence()).isEmpty();
            assertThat(record.getTotalElevationGain()).isEmpty();
        }

        @Test
        @DisplayName("선택 항목을 채우면 값이 담긴다")
        void optionalMetricsAreKeptWhenGiven() {
            // when
            RunningRecord record = validRecord()
                    .avgCadence(168)
                    .totalElevationGain(120)
                    .build();

            // then
            assertThat(record.getAvgCadence()).isPresent();
            assertThat(record.getAvgCadence().get().stepsPerMinute()).isEqualTo(168);
            assertThat(record.getTotalElevationGain().get().meters()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("식별자 테스트")
    class IdentityTest {

        @Test
        @DisplayName("러닝 종료로 만든 기록은 아직 ID가 없다")
        void finishedRecordHasNoId() {
            // when
            RunningRecord record = validRecord().build();

            // then -> PK는 DB가 INSERT 시점에 정한다
            assertThat(record.isNew()).isTrue();
            assertThat(record.getRunningRecordId()).isEmpty();
        }

        @Test
        @DisplayName("복원한 기록은 ID를 갖는다")
        void restoredRecordHasId() {
            // when
            RunningRecord record = RunningRecord.restore(77L)
                    .runningRoomId(125L)
                    .userId(UuidCreator.getTimeOrderedEpoch())
                    .avgPace(330)
                    .totalDistance(3_000)
                    .totalDuration(990)
                    .totalCalories(210)
                    .gpsTrackKey("gps-tracks/a.json")
                    .routePolyline("_p~iF")
                    .startAt(START)
                    .endAt(END)
                    .weatherCode(61)
                    .temperature(new BigDecimal("23.4"))
                    .splits(validSplits())
                    .build();

            // then
            assertThat(record.isNew()).isFalse();
            assertThat(record.getRunningRecordId()).contains(new RunningRecordId(77L));
        }

        @Test
        @DisplayName("구간도 저장 전에는 ID가 없다")
        void newSplitHasNoId() {
            // when -> 구간은 기록을 거쳐야만 조립된다
            RunningRecord record = validRecord().build();

            // then -> 원시 입력에 ID가 없으면 조립된 구간에도 없다
            assertThat(record.getSplits()).allSatisfy(split -> {
                assertThat(split.isNew()).isTrue();
                assertThat(split.getRunningSplitId()).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("필수값 테스트")
    class RequiredValueTest {

        @Test
        @DisplayName("칼로리가 없으면 예외가 발생한다")
        void caloriesIsRequired() {
            // when & then -> int로 받으면 안 넣은 값이 조용히 0이 되므로 null로 구분한다
            assertThatThrownBy(() -> validRecord().totalCalories(null).build())
                    .isInstanceOf(CaloriesRequiredException.class);
        }

        @Test
        @DisplayName("날씨 코드가 없으면 예외가 발생한다")
        void weatherCodeIsRequired() {
            // when & then
            assertThatThrownBy(() -> validRecord().weatherCode(null).build())
                    .isInstanceOf(WeatherCodeRequiredException.class);
        }

        @Test
        @DisplayName("기온이 없으면 예외가 발생한다")
        void temperatureIsRequired() {
            // when & then -> 기온은 VO가 직접 막는다
            assertThatThrownBy(() -> validRecord().temperature(null).build())
                    .isInstanceOf(TemperatureRequiredException.class);
        }

        @Test
        @DisplayName("구간이 없으면 예외가 발생한다")
        void splitsAreRequired() {
            // when & then
            assertThatThrownBy(() -> validRecord().splits(List.of()).build())
                    .isInstanceOf(SplitsRequiredException.class);
            assertThatThrownBy(() -> validRecord().splits(null).build())
                    .isInstanceOf(SplitsRequiredException.class);
        }
    }

    @Nested
    @DisplayName("구간 번호 테스트")
    class SplitNumberSequenceTest {

        @Test
        @DisplayName("구간 번호가 1부터 시작하지 않으면 예외가 발생한다")
        void splitNumberMustStartAtOne() {
            // given -> 2, 3으로 시작하는 구간 목록
            List<SplitDraft> splits = List.of(
                    split(2, 0, 120, START, START.plusMinutes(5)),
                    split(3, 120, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitNumberNotSequentialException.class);
        }

        @Test
        @DisplayName("구간 번호가 중간에 빠지면 예외가 발생한다")
        void splitNumberMustNotSkip() {
            // given -> 1, 3 (2번 누락)
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START, START.plusMinutes(5)),
                    split(3, 120, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then -> 구간 하나만 보면 3은 정상이라 기록이 봐야 잡힌다
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitNumberNotSequentialException.class);
        }

        @Test
        @DisplayName("구간 번호가 목록 순서와 어긋나면 예외가 발생한다")
        void splitNumberMustMatchListOrder() {
            // given -> 2, 1 순으로 담긴 목록
            List<SplitDraft> splits = List.of(
                    split(2, 0, 120, START, START.plusMinutes(5)),
                    split(1, 120, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitNumberNotSequentialException.class);
        }
    }

    @Nested
    @DisplayName("구간 경로 테스트")
    class SplitRouteTest {

        @Test
        @DisplayName("첫 구간이 0번 좌표에서 시작하지 않으면 예외가 발생한다")
        void firstSplitMustStartAtOrigin() {
            // given -> 5번 좌표부터 시작
            List<SplitDraft> splits = List.of(
                    split(1, 5, 120, START, START.plusMinutes(5)),
                    split(2, 120, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then -> 경로 앞부분이 잘린 기록을 막는다
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitRouteNotStartingAtOriginException.class);
        }

        @Test
        @DisplayName("구간 경로가 겹치지 않으면 예외가 발생한다")
        void splitRoutesMustShareBoundary() {
            // given -> 120에서 끝났는데 다음이 121에서 시작(한 칸 뜸)
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START, START.plusMinutes(5)),
                    split(2, 121, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then -> 경계가 안 붙으면 지도에서 선이 끊긴다
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitRouteNotConnectedException.class);
        }

        @Test
        @DisplayName("구간 경로가 뒤로 물러나면 예외가 발생한다")
        void splitRoutesMustNotGoBackward() {
            // given -> 120에서 끝났는데 다음이 100에서 시작
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START, START.plusMinutes(5)),
                    split(2, 100, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitRouteNotConnectedException.class);
        }
    }

    @Nested
    @DisplayName("구간 시각 테스트")
    class SplitPeriodTest {

        @Test
        @DisplayName("구간이 기록 시작보다 앞서면 예외가 발생한다")
        void splitMustNotStartBeforeRecord() {
            // given
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START.minusMinutes(1), START.plusMinutes(5)),
                    split(2, 120, 245, START.plusMinutes(5), START.plusMinutes(11)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitPeriodOutOfRecordException.class);
        }

        @Test
        @DisplayName("구간이 기록 종료보다 뒤면 예외가 발생한다")
        void splitMustNotEndAfterRecord() {
            // given
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START, START.plusMinutes(5)),
                    split(2, 120, 245, START.plusMinutes(5), END.plusMinutes(1)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitPeriodOutOfRecordException.class);
        }

        @Test
        @DisplayName("기록 경계와 딱 붙는 구간은 허용된다")
        void splitOnRecordBoundaryIsAllowed() {
            // when & then -> 첫 구간 시작과 마지막 구간 끝이 기록과 같은 것이 정상이다
            RunningRecord record = validRecord()
                    .splits(List.of(
                            split(1, 0, 120, START, START.plusMinutes(15)),
                            split(2, 120, 245, START.plusMinutes(15), END)))
                    .build();

            assertThat(record.getSplits()).hasSize(2);
        }

        @Test
        @DisplayName("구간 시각이 겹치면 예외가 발생한다")
        void splitPeriodsMustNotOverlap() {
            // given -> 구간1이 10분에 끝나는데 구간2가 8분에 시작
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START, START.plusMinutes(10)),
                    split(2, 120, 245, START.plusMinutes(8), START.plusMinutes(16)));

            // when & then
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitPeriodNotSequentialException.class);
        }

        @Test
        @DisplayName("구간 시각이 역행하면 예외가 발생한다")
        void splitPeriodsMustNotGoBackward() {
            // given -> 경로는 이어지는데 시각만 거꾸로 간다
            List<SplitDraft> splits = List.of(
                    split(1, 0, 120, START.plusMinutes(20), START.plusMinutes(25)),
                    split(2, 120, 245, START.plusMinutes(5), START.plusMinutes(10)));

            // when & then -> 경로와 시각이 서로 다른 이야기를 하는 상태를 막는다
            assertThatThrownBy(() -> validRecord().splits(splits).build())
                    .isInstanceOf(SplitPeriodNotSequentialException.class);
        }

        @Test
        @DisplayName("구간 경계가 딱 붙는 것은 허용된다")
        void adjacentSplitPeriodsAreAllowed() {
            // when & then -> GPS 버퍼가 만드는 값은 앞 구간 끝과 뒤 구간 시작이 같다
            RunningRecord record = validRecord().build();

            assertThat(record.getSplits().get(0).getPeriod().endAt())
                    .isEqualTo(record.getSplits().get(1).getPeriod().startAt());
        }
    }

    @Nested
    @DisplayName("구간 목록 보호 테스트")
    class SplitsImmutabilityTest {

        @Test
        @DisplayName("구간 목록은 밖에서 바꿀 수 없다")
        void splitsAreImmutable() {
            // given
            RunningRecord record = validRecord().build();

            // when & then -> 밖에서는 구간을 만들 수조차 없고(SplitDraft까지만 채운다),
            //                이미 조립된 목록도 손댈 수 없다
            assertThatThrownBy(() -> record.getSplits().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> record.getSplits().remove(0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("생성에 넘긴 목록을 바꿔도 기록은 영향받지 않는다")
        void splitsAreDefensivelyCopied() {
            // given
            List<SplitDraft> given = new ArrayList<>(validSplits());
            RunningRecord record = validRecord().splits(given).build();

            // when
            given.clear();

            // then -> 방어 복사가 없으면 기록의 구간이 통째로 사라진다
            assertThat(record.getSplits()).hasSize(3);
        }
    }
}
