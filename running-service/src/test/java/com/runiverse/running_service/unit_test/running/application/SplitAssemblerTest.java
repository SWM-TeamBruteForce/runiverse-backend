package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.finish.BoundaryPoint;
import com.runiverse.running_service.application.running.command.finish.SplitAssembler;
import com.runiverse.running_service.application.running.command.finish.TrackDistance;
import com.runiverse.running_service.application.running.command.finish.TrackResampler;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.SplitDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("구간 조립 단위 테스트")
public class SplitAssemblerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final int INTERVAL = 10;
    private static final int TARGET = 5_000;
    private static final double METERS_PER_DEGREE = 111_320.0;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    // UserId가 UUIDv7만 받는다 — randomUUID()는 v4라 거부된다
    private static final UUID USER_ID = UUID.fromString("01a02344-364f-7d53-860a-c4f967cf1dbd");
    private static final double ELEVATION_NOISE = 3.0;

    private static TrackPoint point(long sequence, double latitude, Double altitude,
                                    Integer cadence, LocalDateTime at) {
        return new TrackPoint(sequence, latitude, 127.0, altitude, 5.0, null, null, cadence,
                null, at);
    }

    // 북쪽으로 초당 stepMeters씩 달리는 트랙
    private static List<TrackPoint> track(int count, double stepMeters, Double altitude,
                                          Integer cadence) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(point(i, 37.5 + i * stepMeters / METERS_PER_DEGREE, altitude, cadence,
                    START.plusSeconds(i)));
        }
        return points;
    }

    private static List<SplitDraft> assemble(List<TrackPoint> points) {
        List<BoundaryPoint> boundaries = TrackResampler.resample(
                points, TrackDistance.cumulativeMeters(points), TARGET, INTERVAL);
        return SplitAssembler.assemble(boundaries, points, INTERVAL, WEIGHT, ELEVATION_NOISE);
    }

    @Test
    @DisplayName("구간 번호는 1부터 빠짐없이 이어진다")
    void splitNumbersAreSequentialFromOne() {
        // when
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));

        // then -> RunningRecord가 이 순서를 검증한다
        assertThat(drafts).hasSize(TARGET / INTERVAL);
        for (int i = 0; i < drafts.size(); i++) {
            assertThat(drafts.get(i).splitNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("경로 인덱스가 구간마다 한 점씩 겹치며 이어진다")
    void routeIndexesConnectSharingOnePoint() {
        // when
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));

        // then -> 첫 구간은 0에서 시작하고, 구간 N의 끝은 N+1의 시작과 같아야 한다
        assertThat(drafts.get(0).routeStartIndex()).isZero();
        for (int i = 0; i < drafts.size() - 1; i++) {
            assertThat(drafts.get(i).routeEndIndex())
                    .isEqualTo(drafts.get(i + 1).routeStartIndex());
        }
    }

    @Test
    @DisplayName("구간 시간의 합이 시작부터 끝까지의 시간과 정확히 맞는다")
    void durationsSumToTotalElapsed() {
        // given
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));

        // when
        int sum = drafts.stream().mapToInt(SplitDraft::duration).sum();

        // then -> total_duration은 구간 duration의 합이다.
        // 구간마다 따로 반올림하면 500개에서 오차가 쌓여 여기가 어긋난다
        SplitDraft last = drafts.get(drafts.size() - 1);
        assertThat(sum).isEqualTo(
                (int) java.time.Duration.between(drafts.get(0).startAt(), last.endAt())
                        .toSeconds());
    }

    @Test
    @DisplayName("구간 시각은 앞 구간 끝에서 이어지고 항상 증가한다")
    void periodsChainForwardWithoutOverlap() {
        // given
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));

        // then -> 끝 == 시작이면 RunningPeriod가 터진다
        for (SplitDraft draft : drafts) {
            assertThat(draft.endAt()).isAfter(draft.startAt());
        }
        for (int i = 0; i < drafts.size() - 1; i++) {
            assertThat(drafts.get(i).endAt()).isEqualTo(drafts.get(i + 1).startAt());
        }
    }

    @Test
    @DisplayName("좌표 시각이 같은 초에 몰려도 구간 시간이 0이 되지 않는다")
    void neverProducesZeroDuration() {
        // given -> 같은 초에 여러 좌표가 들어온 트랙(재연결 재전송·GPS 튐)
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            points.add(point(i, 37.5 + i * 12.0 / METERS_PER_DEGREE, null, null,
                    START.plusSeconds(i / 3)));   // 3점마다 1초
        }

        // when
        List<SplitDraft> drafts = assemble(points);

        // then -> ElapsedTime(MIN 1)과 RunningPeriod(끝 > 시작)를 둘 다 통과해야 한다
        assertThat(drafts).isNotEmpty();
        for (SplitDraft draft : drafts) {
            assertThat(draft.duration()).isGreaterThanOrEqualTo(1);
            assertThat(draft.endAt()).isAfter(draft.startAt());
        }
    }

    @Test
    @DisplayName("케이던스는 구간에 속한 실측점 평균으로 낸다")
    void cadenceComesFromRealPoints() {
        // when
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, 168));

        // then -> 보간점에는 센서값이 없으므로 실측점에서 나와야 한다
        assertThat(drafts).allSatisfy(draft ->
                assertThat(draft.avgCadence()).isEqualTo(168));
    }

    @Test
    @DisplayName("케이던스를 못 잰 트랙은 null로 둔다")
    void cadenceIsNullWhenDeviceCannotMeasure() {
        // when
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));

        // then
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.avgCadence()).isNull());
    }

    @Test
    @DisplayName("고도가 평평하면 노이즈 임계값에 걸려 null이다")
    void flatElevationIsFilteredAsNoise() {
        // when -> 고도가 계속 18.4m
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, 18.4, null));

        // then -> 10m 구간에서 대체로 null이 되는 경로다
        assertThat(drafts).allSatisfy(draft ->
                assertThat(draft.elevationChange()).isNull());
    }

    @Test
    @DisplayName("임계값을 넘는 고도 변화는 남긴다")
    void keepsElevationChangeAboveThreshold() {
        // given -> 초당 5m씩 올라가는 트랙. 10m 구간(약 3.5초)이면 임계값 3m를 넘는다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(point(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, 10.0 + i * 5.0, null,
                    START.plusSeconds(i)));
        }

        // when
        List<SplitDraft> drafts = assemble(points);

        // then
        assertThat(drafts).anySatisfy(draft ->
                assertThat(draft.elevationChange()).isNotNull().isPositive());
    }

    @Test
    @DisplayName("조립한 구간 500개가 RunningRecord 검증을 통과한다")
    void assembledSplitsSatisfyRecordInvariants() {
        // given
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, 168));
        int totalDuration = drafts.stream().mapToInt(SplitDraft::duration).sum();

        // when & then -> 지금까지 만든 조각이 도메인 불변식을 실제로 만족하는지 보는 관문이다
        assertThatCode(() -> RunningRecord.finish()
                .runningRoomId(125L)
                .userId(USER_ID)
                .avgPace(totalDuration * 1_000 / TARGET)
                .totalDistance(TARGET)
                .totalDuration(totalDuration)
                .totalCalories(368)
                .gpsTrackKey("gps-tracks/u/125/2026-08-27.json")
                .routePolyline("u{~vFvyys@fS]pT_@")
                .startAt(drafts.get(0).startAt())
                .endAt(drafts.get(drafts.size() - 1).endAt())
                .weatherCode(0)
                .temperature(new BigDecimal("15.0"))
                .splits(drafts)
                .build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경계가 없으면 구간도 없다")
    void noBoundariesProduceNoSplits() {
        // when & then -> 기록 없이 상태만 확정하는 경로
        assertThat(SplitAssembler.assemble(List.of(), List.of(), INTERVAL, WEIGHT,
                ELEVATION_NOISE)).isEmpty();
    }
}
