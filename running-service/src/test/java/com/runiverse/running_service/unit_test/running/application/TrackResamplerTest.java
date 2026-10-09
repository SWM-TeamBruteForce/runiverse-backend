package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.FilteredTrack;
import com.runiverse.running_service.application.running.common.TrackDistance;
import com.runiverse.running_service.application.running.common.TrackResampler;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.runiverse.running_service.support.TrackFilterFixtures.unfiltered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("트랙 재표본화 단위 테스트")
public class TrackResamplerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final int INTERVAL = 10;
    private static final int TARGET = 5_000;
    // 위도 1도 ≈ 111,320m — 북쪽으로 곧게 달리는 트랙을 만들 때 쓴다
    private static final double METERS_PER_DEGREE = 111_320.0;

    private static TrackPoint point(long sequence, double latitude, double longitude,
                                    LocalDateTime at) {
        return new TrackPoint(sequence, latitude, longitude, null, 5.0, null, null, null, null, at);
    }

    // 북쪽으로 일정 간격 달리는 트랙 — 1초에 stepMeters씩
    private static List<TrackPoint> straightTrack(int count, double stepMeters) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(point(i, 37.5 + i * stepMeters / METERS_PER_DEGREE, 127.0,
                    START.plusSeconds(i)));
        }
        return points;
    }

    private static List<BoundaryPoint> resample(List<TrackPoint> points, int target) {
        return TrackResampler.resample(unfiltered(points), target, INTERVAL);
    }

    private static TrackPoint asPoint(BoundaryPoint boundary) {
        return point(0, boundary.latitude(), boundary.longitude(), boundary.recordedAt());
    }

    @Test
    @DisplayName("경계 거리는 0부터 간격만큼씩 늘어난다")
    void boundaryDistancesStepByInterval() {
        // given -> 2.8m씩 1,800점 ≈ 5,040m
        List<BoundaryPoint> boundaries = resample(straightTrack(1_800, 2.8), TARGET);

        // then -> 목표 5,000m면 0,10,…,5000으로 501개다
        assertThat(boundaries).hasSize(TARGET / INTERVAL + 1);
        for (int i = 0; i < boundaries.size(); i++) {
            assertThat(boundaries.get(i).distanceMeters()).isEqualTo(i * INTERVAL);
        }
    }

    @Test
    @DisplayName("목표를 넘겨 뛰면 목표 지점에서 끊는다")
    void stopsAtTargetWhenOverrun() {
        // given -> 약 5,040m를 뛰었다
        List<TrackPoint> points = straightTrack(1_800, 2.8);
        double total = TrackDistance.cumulativeMeters(points)[points.size() - 1];
        assertThat(total).isGreaterThan(TARGET);

        // when
        List<BoundaryPoint> boundaries = resample(points, TARGET);

        // then -> 초과분은 기록에서 빠진다(S3 원본에는 남는다)
        assertThat(boundaries.get(boundaries.size() - 1).distanceMeters()).isEqualTo(TARGET);
    }

    @Test
    @DisplayName("목표에 못 미치면 마지막으로 완성한 경계까지만 만든다")
    void stopsAtLastCompletedBoundaryWhenShort() {
        // given -> 약 3,205m (경계 3,200m를 채우고 5m가 남는다)
        List<TrackPoint> points = straightTrack(1_146, 2.8);
        double total = TrackDistance.cumulativeMeters(points)[points.size() - 1];
        assertThat(total).isBetween(3_200.0, 3_210.0);

        // when
        List<BoundaryPoint> boundaries = resample(points, TARGET);

        // then -> 남은 5m를 버려야 total_duration(구간 합)과 총거리가 어긋나지 않는다
        assertThat(boundaries.get(boundaries.size() - 1).distanceMeters()).isEqualTo(3_200);
    }

    @Test
    @DisplayName("경계점 사이의 실제 거리가 간격과 일치한다")
    void spacingBetweenBoundariesMatchesInterval() {
        // given
        List<BoundaryPoint> boundaries = resample(straightTrack(1_800, 2.8), TARGET);

        // then -> 보간이 틀리면 거리는 10m라 적어놓고 실제로는 아닌 상태가 된다
        for (int i = 1; i < boundaries.size(); i++) {
            double meters = TrackDistance.between(
                    asPoint(boundaries.get(i - 1)), asPoint(boundaries.get(i)));
            assertThat(meters).isCloseTo(INTERVAL, within(0.1));
        }
    }

    @Test
    @DisplayName("첫 경계는 시작점 좌표와 시각을 그대로 쓴다")
    void firstBoundaryIsTheStartPoint() {
        // given
        List<TrackPoint> points = straightTrack(100, 2.8);

        // when
        BoundaryPoint first = resample(points, TARGET).get(0);

        // then -> 0m는 보간할 것이 없다
        assertThat(first.distanceMeters()).isZero();
        assertThat(first.latitude()).isEqualTo(points.get(0).latitude());
        assertThat(first.longitude()).isEqualTo(points.get(0).longitude());
        assertThat(first.recordedAt()).isEqualTo(points.get(0).recordedAt());
    }

    @Test
    @DisplayName("경계 시각은 뒤로 가지 않는다")
    void boundaryTimesNeverGoBackward() {
        // given
        List<BoundaryPoint> boundaries = resample(straightTrack(1_800, 2.8), TARGET);

        // then -> 구간 duration이 음수가 되면 RunningRecord가 구간 검증에서 막는다
        for (int i = 1; i < boundaries.size(); i++) {
            assertThat(boundaries.get(i).recordedAt())
                    .isAfterOrEqualTo(boundaries.get(i - 1).recordedAt());
        }
    }

    @Test
    @DisplayName("sourceIndex는 뒤로 가지 않고 실측점 범위 안에 있다")
    void sourceIndexStaysInRangeAndMovesForward() {
        // given
        List<TrackPoint> points = straightTrack(1_800, 2.8);

        // when
        List<BoundaryPoint> boundaries = resample(points, TARGET);

        // then -> 구간별 고도·케이던스가 이 인덱스로 실측점을 잘라 쓴다
        int previous = -1;
        for (BoundaryPoint boundary : boundaries) {
            assertThat(boundary.sourceIndex()).isBetween(0, points.size() - 1);
            assertThat(boundary.sourceIndex()).isGreaterThanOrEqualTo(previous);
            previous = boundary.sourceIndex();
        }
    }

    @Test
    @DisplayName("제자리에 머문 구간이 있어도 좌표가 깨지지 않는다")
    void handlesStationarySegmentsWithoutDividingByZero() {
        // given -> 중간에 같은 자리 좌표가 연속으로 들어온다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            points.add(point(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, 127.0, START.plusSeconds(i)));
        }
        double frozenLatitude = points.get(49).latitude();
        for (int i = 50; i < 60; i++) {
            points.add(point(i, frozenLatitude, 127.0, START.plusSeconds(i)));
        }
        for (int i = 60; i < 120; i++) {
            points.add(point(i, frozenLatitude + (i - 59) * 2.8 / METERS_PER_DEGREE, 127.0,
                    START.plusSeconds(i)));
        }

        // when
        List<BoundaryPoint> boundaries = resample(points, TARGET);

        // then -> 0으로 나누면 NaN이 좌표에 실려 폴리라인이 통째로 깨진다
        assertThat(boundaries).isNotEmpty();
        for (BoundaryPoint boundary : boundaries) {
            assertThat(boundary.latitude()).isNotNaN();
            assertThat(boundary.longitude()).isNotNaN();
        }
    }

    @Test
    @DisplayName("한 구간도 못 채우면 경계가 없다")
    void tooShortTrackProducesNoBoundaries() {
        // given -> 약 5.6m만 이동
        List<TrackPoint> points = straightTrack(3, 2.8);

        // when & then -> 기록 없이 상태만 확정하는 경로로 흘러간다
        assertThat(resample(points, TARGET)).isEmpty();
    }

    @Test
    @DisplayName("좌표가 없거나 하나뿐이면 경계가 없다")
    void emptyOrSinglePointProducesNoBoundaries() {
        // when & then
        assertThat(resample(List.of(), TARGET)).isEmpty();
        assertThat(resample(List.of(point(1, 37.5, 127.0, START)), TARGET)).isEmpty();
    }

    // Z→A를 달리고 A~D에서 12초 멈췄다가 E·F로 달린다. 필터가 A~D 칸을 0으로 한 결과를 그대로 넣는다
    private static FilteredTrack stoppedTrack() {
        double[] north = {0, 12, 14, 11, 13, 25, 37};
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < north.length; i++) {
            points.add(point(i, 37.5 + north[i] / METERS_PER_DEGREE, 127.0,
                    START.plusSeconds(i * 4L)));
        }
        return new FilteredTrack(points,
                new double[]{0, 12, 12, 12, 12, 24, 36},
                new double[]{0, 4, 4, 4, 4, 8, 12},
                null);
    }

    @Test
    @DisplayName("필터가 0으로 한 칸에는 경계점이 찍히지 않는다")
    void noBoundaryOnRemovedEdges() {
        // when
        List<BoundaryPoint> boundaries = TrackResampler.resample(stoppedTrack(), TARGET, INTERVAL);

        // then -> 10m는 Z→A, 20m는 정지를 건너뛴 D→E, 30m는 E→F 위다
        assertThat(boundaries).extracting(BoundaryPoint::sourceIndex).containsExactly(0, 0, 4, 5);
        assertThat(boundaries.get(2).latitude())
                .isCloseTo(37.5 + 21 / METERS_PER_DEGREE, within(1e-9));
    }

    @Test
    @DisplayName("움직인 시간은 거리와 같은 비율로 보간되고 실제 시각은 따로 남는다")
    void movingSecondsInterpolateWithDistance() {
        // when
        List<BoundaryPoint> boundaries = TrackResampler.resample(stoppedTrack(), TARGET, INTERVAL);

        // then -> 10~20m 구간은 실제로 15.3초지만 움직인 시간은 3.3초다 — 멈춘 12초가 빠진다
        double[] expected = {0.0, 10.0 / 3, 20.0 / 3, 10.0};
        for (int i = 0; i < expected.length; i++) {
            assertThat(boundaries.get(i).movingSeconds()).isCloseTo(expected[i], within(1e-9));
        }
        assertThat(boundaries).extracting(b -> Duration.between(START, b.recordedAt()).toMillis())
                .containsExactly(0L, 3_333L, 18_666L, 22_000L);
    }
}
