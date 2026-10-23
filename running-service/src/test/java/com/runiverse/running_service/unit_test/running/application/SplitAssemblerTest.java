package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.CalorieCalculator;
import com.runiverse.running_service.application.running.common.FilteredTrack;
import com.runiverse.running_service.application.running.common.SplitAssembler;
import com.runiverse.running_service.application.running.common.TrackResampler;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.SegmentDraft;
import com.runiverse.running_service.domain.running.record.SplitDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.runiverse.running_service.support.TrackFilterFixtures.unfiltered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

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
                null, at, 0);
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
                unfiltered(points), TARGET, INTERVAL);
        return SplitAssembler.assemble(List.of(boundaries), points, INTERVAL, WEIGHT, ELEVATION_NOISE);
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
    @DisplayName("구간 칼로리의 합이 전체 거리·시간으로 낸 칼로리와 맞는다")
    void caloriesSumToTotalCalories() {
        // given
        List<SplitDraft> drafts = assemble(track(1_800, 2.8, null, null));
        int totalDuration = drafts.stream().mapToInt(SplitDraft::duration).sum();

        // when
        int sum = drafts.stream().mapToInt(SplitDraft::calories).sum();

        // then -> 10m 구간 하나는 1kcal에 못 미친다. 구간마다 반올림하면 500개가 전부 1이 돼
        //         합이 500으로 부풀고, 누적값으로 반올림하면 전체 계산(387)과 맞는다
        int total = CalorieCalculator.kcal(totalDuration * 1_000 / TARGET, totalDuration, WEIGHT);
        assertThat(sum).isCloseTo(total, within(1));
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
                .segments(List.of(new SegmentDraft(1, "u{~vFvyys@fS]pT_@")))
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

    @Test
    @DisplayName("구간 시각은 실제 시각, duration은 움직인 시간이다")
    void splitTimesAreRealAndDurationIsMoving() {
        // given -> Z→A를 달리고 A~D에서 12초 멈췄다가 E·F로 달린다. 필터가 A~D 칸을 0으로 한 결과다
        double[] north = {0, 12, 14, 11, 13, 25, 37};
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < north.length; i++) {
            points.add(point(i, 37.5 + north[i] / METERS_PER_DEGREE, null, null,
                    START.plusSeconds(i * 4L)));
        }
        FilteredTrack track = new FilteredTrack(points,
                new double[]{0, 12, 12, 12, 12, 24, 36},
                new double[]{0, 4, 4, 4, 4, 8, 12},
                new boolean[points.size()],   // 정지는 끊지 않는다
                null);
        List<BoundaryPoint> boundaries = TrackResampler.resample(track, TARGET, INTERVAL);

        // when
        List<SplitDraft> drafts = SplitAssembler.assemble(List.of(boundaries), points, INTERVAL, WEIGHT,
                ELEVATION_NOISE);

        // then -> 멈춘 자리를 걸친 2구간은 시각이 3~19초로 남고 duration은 4초다
        assertThat(drafts).extracting(SplitDraft::duration).containsExactly(3, 4, 3);
        assertThat(drafts.get(1).startAt()).isEqualTo(START.plusSeconds(3));
        assertThat(drafts.get(1).endAt()).isEqualTo(START.plusSeconds(19));
        assertThat(drafts.get(2).endAt()).isEqualTo(START.plusSeconds(22));
    }

    // 직선 트랙의 경계점을 P7까지와 P8부터 두 조각으로 나눈다 — P7→P8 사이에서 끊긴 것처럼 둔다
    private static List<List<BoundaryPoint>> twoSegments(List<BoundaryPoint> boundaries) {
        return List.of(boundaries.subList(0, 8), boundaries.subList(8, boundaries.size()));
    }

    @Test
    @DisplayName("구간은 끝 경계점이 있는 조각에 속하고 경로 범위는 그 조각 안의 번호다")
    void splitsBelongToSegmentOfEndBoundary() {
        // given
        List<TrackPoint> points = track(61, 2.5, null, null);
        List<BoundaryPoint> boundaries = TrackResampler.resample(unfiltered(points), TARGET, INTERVAL);

        // when
        List<SplitDraft> drafts = SplitAssembler.assemble(twoSegments(boundaries), points, INTERVAL,
                WEIGHT, ELEVATION_NOISE);

        // then -> 구간 1~7은 1번 조각 [n-1, n], 구간 8부터는 2번 조각이다. 번호는 조각과 무관하게 이어진다
        int last = boundaries.size() - 1;
        assertThat(drafts).hasSize(last);
        assertThat(drafts).extracting(SplitDraft::splitNumber)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, last).boxed().toList());
        assertThat(drafts.subList(0, 7)).allSatisfy(draft -> {
            assertThat(draft.segmentNumber()).isEqualTo(1);
            assertThat(draft.routeStartIndex()).isEqualTo(draft.splitNumber() - 1);
            assertThat(draft.routeEndIndex()).isEqualTo(draft.splitNumber());
        });
        assertThat(drafts.subList(7, last)).allSatisfy(draft ->
                assertThat(draft.segmentNumber()).isEqualTo(2));
        assertThat(drafts.get(8).routeStartIndex()).isZero();
        assertThat(drafts.get(8).routeEndIndex()).isEqualTo(1);
        // 마지막 구간의 끝은 2번 조각의 마지막 점이다
        assertThat(drafts.getLast().routeEndIndex()).isEqualTo(last - 8);
    }

    @Test
    @DisplayName("끊긴 곳에 걸친 구간은 뒤 조각의 0번 점 하나만 가리키고 거리·시간은 그대로다")
    void splitAcrossBreakPointsOnlyToNextSegmentOrigin() {
        // given
        List<TrackPoint> points = track(61, 2.5, null, null);
        List<BoundaryPoint> boundaries = TrackResampler.resample(unfiltered(points), TARGET, INTERVAL);

        // when
        List<SplitDraft> drafts = SplitAssembler.assemble(twoSegments(boundaries), points, INTERVAL,
                WEIGHT, ELEVATION_NOISE);

        // then -> 구간 8(P7→P8)은 시작 경계점이 1번 조각에 있다. 범위를 2번 조각 안에 두면 [0, 0]이다
        SplitDraft across = drafts.get(7);
        assertThat(across.splitNumber()).isEqualTo(8);
        assertThat(across.segmentNumber()).isEqualTo(2);
        assertThat(across.routeStartIndex()).isZero();
        assertThat(across.routeEndIndex()).isZero();
        // 경로만 점 하나일 뿐 10m 구간 값은 다른 구간과 같다
        assertThat(across.distance()).isEqualTo(INTERVAL);
        assertThat(across.duration()).isEqualTo(drafts.get(6).duration());
        assertThat(across.startAt()).isEqualTo(drafts.get(6).endAt());
    }

    @Test
    @DisplayName("두 조각으로 나눈 구간도 도메인 불변식을 만족한다")
    void splitsAcrossSegmentsSatisfyRecordInvariants() {
        // given
        List<TrackPoint> points = track(61, 2.5, null, null);
        List<BoundaryPoint> boundaries = TrackResampler.resample(unfiltered(points), TARGET, INTERVAL);
        List<SplitDraft> drafts = SplitAssembler.assemble(twoSegments(boundaries), points, INTERVAL,
                WEIGHT, ELEVATION_NOISE);
        int totalDuration = drafts.stream().mapToInt(SplitDraft::duration).sum();
        int totalDistance = drafts.size() * INTERVAL;

        // when & then -> 조립 규칙과 기록 검증 규칙([0, 0]으로 조각 시작)이 어긋나면 여기서 터진다
        assertThatCode(() -> RunningRecord.finish()
                .runningRoomId(125L)
                .userId(USER_ID)
                .avgPace(totalDuration * 1_000 / totalDistance)
                .totalDistance(totalDistance)
                .totalDuration(totalDuration)
                .totalCalories(10)
                .gpsTrackKey("gps-tracks/u/125/2026-08-27.json")
                .segments(List.of(new SegmentDraft(1, "u{~vFvyys@"), new SegmentDraft(2, "fS]pT_@")))
                .startAt(drafts.get(0).startAt())
                .endAt(drafts.get(drafts.size() - 1).endAt())
                .weatherCode(0)
                .temperature(new BigDecimal("15.0"))
                .splits(drafts)
                .build()).doesNotThrowAnyException();
    }

    // 북쪽으로 초당 0.5m 걷는 좌표 — 10m 구간 하나에 표본이 스무 개쯤 들어가 앞뒤 순고도차를 따로 잴 수 있다
    private static TrackPoint walking(int i, Double altitude, int pauseSegment) {
        return new TrackPoint(i, 37.5 + i * 0.5 / METERS_PER_DEGREE, 127.0, altitude, 5.0,
                null, null, null, null, START.plusSeconds(i), pauseSegment);
    }

    // 점 0~29는 일시정지 전, 점 30부터는 재개 뒤다. 재개하면서 고도가 100m로 뛴다(일시정지 중 오른 것).
    // 두 쪽 모두 점마다 0.5m씩 오른다
    private static List<TrackPoint> pausedWalk(boolean altitudeBeforePause) {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            Double altitude = i < 30
                    ? (altitudeBeforePause ? Double.valueOf(i * 0.5) : null)
                    : Double.valueOf(100.0 + (i - 30) * 0.5);
            points.add(walking(i, altitude, i < 30 ? 0 : 1));
        }
        return points;
    }

    private static List<SplitDraft> assembleWalk(List<TrackPoint> points) {
        List<BoundaryPoint> boundaries = TrackResampler.resample(unfiltered(points), TARGET, INTERVAL);
        return SplitAssembler.assemble(List.of(boundaries), points, INTERVAL, WEIGHT, ELEVATION_NOISE);
    }

    @Test
    @DisplayName("일시정지를 걸친 구간의 순고도차는 경계 앞과 뒤를 따로 내서 더한다")
    void elevationChangeAcrossPauseSumsBothSides() {
        // when -> 2번 구간(약 10~20m)이 점 30의 경계를 걸친다
        List<SplitDraft> drafts = assembleWalk(pausedWalk(true));

        // then -> 앞쪽 약 +4.5m와 뒤쪽 약 +5m의 합이다. 끝 − 시작으로 내면 일시정지 중 오른 고도까지 +94m쯤 된다
        assertThat(drafts.get(1).elevationChange()).isBetween(8, 11);
    }

    @Test
    @DisplayName("경계 한쪽에만 고도 표본이 있으면 그쪽 순고도차만 쓴다")
    void elevationChangeUsesOneSideWhenOtherHasNoSamples() {
        // when -> 일시정지 전에는 단말이 고도를 못 쟀다
        List<SplitDraft> drafts = assembleWalk(pausedWalk(false));

        // then -> 재개 뒤 약 +5m만 남는다
        assertThat(drafts.get(1).elevationChange()).isBetween(4, 6);
    }

    @Test
    @DisplayName("경계를 걸치지 않은 구간은 지금처럼 끝 − 시작이다")
    void elevationChangeWithoutPauseIsUnchanged() {
        // when -> 1번 구간(약 0~10m)은 일시정지 전이다
        List<SplitDraft> drafts = assembleWalk(pausedWalk(true));

        // then -> 점마다 0.5m씩 스무 칸쯤 올라 약 +10m다
        assertThat(drafts.get(0).elevationChange()).isBetween(9, 11);
    }

    @Test
    @DisplayName("경계 앞뒤에 표본이 하나씩뿐이면 순고도차를 말할 수 없다")
    void elevationChangeIsNullWithSingleSampleEachSide() {
        // given -> 2번 구간 안에서 경계 바로 앞 점 하나와 바로 뒤 점 하나만 고도가 있다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            Double altitude = i == 29 ? Double.valueOf(14.0) : i == 30 ? Double.valueOf(100.0) : null;
            points.add(walking(i, altitude, i < 30 ? 0 : 1));
        }

        // when
        List<SplitDraft> drafts = assembleWalk(points);

        // then -> 지금 규칙이면 둘을 끝 − 시작으로 이어 +86m가 된다
        assertThat(drafts.get(1).elevationChange()).isNull();
    }
}
