package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.FilteredTrack;
import com.runiverse.running_service.application.running.common.TrackFilter;
import com.runiverse.running_service.application.running.common.TrackFilterProperties;
import com.runiverse.running_service.application.running.common.TrackFilterSummary;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("트랙 필터 단위 테스트")
public class TrackFilterTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    // 하버사인이 쓰는 지구 평균 반경(6,371,008.8m) 기준의 위도 1도 — 미터 좌표가 하버사인 거리와 맞는다
    private static final double METERS_PER_DEGREE_LATITUDE = 111_194.93;
    private static final double METERS_PER_DEGREE_LONGITUDE =
            METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(37.5));
    private static final double ACCURACY = 5.0;
    // 미터 좌표를 위경도로 바꾼 근사와 하버사인 거리의 차이를 흡수한다
    private static final double TOLERANCE_METERS = 0.5;

    // 운영 시작값 그대로 — 정확도 50m, 최대 속도 12m/s, 정지 5~8m·10초,
    // 관측 안 된 칸 10초·500m·1.5m/s·×1.3·300초·출발 직후 7m/s
    private static final TrackFilterProperties PROPERTIES = new TrackFilterProperties(
            50.0, 12.0, 5.0, 8.0, 10, 10, 500.0, 1.5, 1.3, 300, 7.0);

    // 출발점 기준 미터 좌표로 트랙을 쌓는다 — north는 북쪽, east는 동쪽, second는 출발부터 초
    private static final class Track {

        private final List<TrackPoint> points = new ArrayList<>();
        private double north;
        private double east;
        private long second;

        Track at(double north, double east, long second) {
            return at(north, east, second, ACCURACY);
        }

        Track at(double north, double east, long second, double accuracy) {
            this.north = north;
            this.east = east;
            this.second = second;
            points.add(new TrackPoint(points.size(),
                    37.5 + north / METERS_PER_DEGREE_LATITUDE,
                    127.0 + east / METERS_PER_DEGREE_LONGITUDE,
                    null, accuracy, null, null, null, null, START.plusSeconds(second)));
            return this;
        }

        // 1초마다 북쪽으로 speed만큼
        Track run(int seconds, double speed) {
            for (int i = 0; i < seconds; i++) {
                at(north + speed, east, second + 1);
            }
            return this;
        }

        // 제자리에서 2초마다 동서로 meters씩 흔들린다 — 기준 위치는 그대로다
        Track jitter(int seconds, double meters) {
            double baseNorth = north;
            double baseEast = east;
            for (int i = 1; i * 2 <= seconds; i++) {
                at(baseNorth, baseEast + (i % 2 == 0 ? meters : -meters), second + 2);
            }
            north = baseNorth;
            east = baseEast;
            return this;
        }

        // 좌표 없이 seconds가 지난 뒤 북쪽으로 meters 떨어진 곳에서 다시 나타난다
        Track gap(long seconds, double meters) {
            return at(north + meters, east, second + seconds);
        }

        List<TrackPoint> points() {
            return points;
        }
    }

    private static double pathMeters(List<TrackPoint> points) {
        FilteredTrack untouched = TrackFilter.apply(points, PROPERTIES);
        return untouched.summary().rawMeters();
    }

    private static double totalSeconds(FilteredTrack track) {
        double[] seconds = track.cumulativeMovingSeconds();
        return seconds[seconds.length - 1];
    }

    @Test
    @DisplayName("오염 없이 달린 트랙은 거리·시간이 그대로 인정된다")
    void cleanRunIsUntouched() {
        // given -> 3m/s로 100초
        List<TrackPoint> points = new Track().at(0, 0, 0).run(100, 3.0).points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.points()).hasSize(101);
        assertThat(filtered.totalMeters()).isCloseTo(300.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(100.0);
        assertThat(filtered.summary().stopCount()).isZero();
        assertThat(filtered.summary().filteredMeters()).isEqualTo(filtered.summary().rawMeters());
    }

    @Test
    @DisplayName("반경 안에 최소 시간 이상 머문 구간은 거리·시간 모두 빠진다")
    void stopIsRemovedFromDistanceAndTime() {
        // given -> A(4초)~D(16초)가 A에서 3m 안을 맴돈다. E에서 반경을 벗어난다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0)        // Z
                .at(12, 0, 4)       // A
                .at(14, 2, 8)       // B
                .at(11, -1, 12)     // C
                .at(13, 0, 16)      // D
                .at(25, 0, 20)      // E
                .at(37, 0, 24)      // F
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then -> A~D는 한 점으로 접힌다. Z→A, D→E, E→F만 센다
        double[] meters = filtered.cumulativeMeters();
        assertThat(meters[2]).isEqualTo(meters[1]);
        assertThat(meters[3]).isEqualTo(meters[1]);
        assertThat(meters[4]).isEqualTo(meters[1]);
        assertThat(filtered.totalMeters()).isCloseTo(36.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(12.0);
        assertThat(filtered.summary().stopCount()).isEqualTo(1);
        assertThat(filtered.summary().stopSeconds()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("최소 시간을 채우지 못하고 반경을 벗어나면 정지가 아니다")
    void shortDwellIsNotStop() {
        // given -> A(4초)~D(13초) 9초만 머문다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0)
                .at(12, 0, 4)
                .at(14, 2, 7)
                .at(11, -1, 10)
                .at(13, 0, 13)
                .at(25, 0, 17)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().stopCount()).isZero();
        assertThat(totalSeconds(filtered)).isEqualTo(17.0);
    }

    @Test
    @DisplayName("멈춘 채 끝난 트랙도 마지막 정지를 뺀다")
    void stopAtEndIsRemoved() {
        // given -> 60초 달리고 20초 제자리
        List<TrackPoint> points = new Track().at(0, 0, 0).run(60, 3.0).jitter(20, 1.0).points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().stopCount()).isEqualTo(1);
        assertThat(filtered.totalMeters()).isLessThanOrEqualTo(180.0 + TOLERANCE_METERS);
        assertThat(totalSeconds(filtered)).isLessThanOrEqualTo(60.0);
    }

    @Test
    @DisplayName("정지 중 한 점이 튀어도 그 점만 빠지고 정지는 이어진다")
    void spikeDuringStopDoesNotBreakStop() {
        // given -> 제자리 20초 중간에 한 점이 100m 튄다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 3.0)
                .at(90, 1, 32).at(90, -1, 34).at(90, 1, 36)
                .at(90, 100, 38)                    // 튄 점
                .at(90, -1, 40).at(90, 1, 42).at(90, -1, 44).at(90, 1, 46).at(90, -1, 48)
                .run(10, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().spikePointCount()).isEqualTo(1);
        assertThat(filtered.summary().stopCount()).isEqualTo(1);
        assertThat(filtered.summary().maxStopSeconds()).isGreaterThanOrEqualTo(16.0);
    }

    @Test
    @DisplayName("일시정지 후 제자리에서 재개하면 빈 시간까지 정지로 빠진다")
    void pauseInPlaceIsStop() {
        // given -> 60초 달리고, 60초 동안 좌표 없이 1m 옆에서 재개한다
        //          (반경과 딱 같은 거리는 부동소수점에 따라 갈려 피한다)
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 3.0)
                .gap(60, 1.0)
                .run(30, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then -> 관측 안 된 칸이 아니라 정지로 처리된다
        assertThat(filtered.summary().stopCount()).isEqualTo(1);
        assertThat(filtered.summary().gapEdgeCount()).isZero();
        assertThat(totalSeconds(filtered)).isLessThanOrEqualTo(90.0);
        assertThat(filtered.totalMeters()).isCloseTo(267.0, within(TOLERANCE_METERS));
    }

    @Test
    @DisplayName("터널처럼 자기 페이스로 짧게 비면 관측 안 된 칸을 인정한다")
    void tunnelGapIsAccepted() {
        // given -> 2.8m/s로 60초, 100초 동안 비었다가 300m 앞(3m/s)에서 나타난다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 2.8)
                .gap(100, 300.0)
                .run(30, 2.8)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().gapAcceptedCount()).isEqualTo(1);
        assertThat(filtered.totalMeters()).isCloseTo(filtered.summary().rawMeters(), within(0.001));
        assertThat(totalSeconds(filtered)).isEqualTo(190.0);
    }

    @Test
    @DisplayName("자기 페이스보다 훨씬 빠르게 비면 거리·시간 모두 빠진다")
    void fasterThanOwnPaceGapIsRejected() {
        // given -> 2.8m/s로 달리다 60초 만에 400m(6.7m/s) 앞에서 나타난다 — 택시
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 2.8)
                .gap(60, 400.0)
                .run(30, 2.8)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().gapRejectedCount()).isEqualTo(1);
        assertThat(filtered.totalMeters()).isCloseTo(90 * 2.8, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(90.0);
    }

    @Test
    @DisplayName("걷기보다 느리게 비면 빠진다")
    void slowerThanWalkingGapIsRejected() {
        // given -> 300초 동안 비었다가 50m 앞(0.17m/s) — 일시정지 중 걸어서 옮김
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 2.8)
                .gap(300, 50.0)
                .run(30, 2.8)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().gapRejectedCount()).isEqualTo(1);
        assertThat(totalSeconds(filtered)).isEqualTo(90.0);
    }

    @Test
    @DisplayName("거리 상한을 넘게 비면 페이스가 맞아도 빠진다")
    void longGapIsRejectedEvenAtOwnPace() {
        // given -> 2.8m/s로 달리다 3,600초 뒤 10km 앞(2.8m/s)에서 나타난다 — 서면에서 해운대
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 2.8)
                .gap(3_600, 10_080.0)
                .run(30, 2.8)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().gapRejectedCount()).isEqualTo(1);
        assertThat(filtered.totalMeters()).isCloseTo(90 * 2.8, within(TOLERANCE_METERS));
    }

    @Test
    @DisplayName("관측 안 된 칸 거리 상한은 경계값까지 인정한다")
    void gapDistanceBoundary() {
        // given -> 2.8m/s로 달리다 170초 뒤 499m·501m(약 2.9m/s) 앞에서 나타난다
        List<TrackPoint> under = new Track().at(0, 0, 0).run(60, 2.8).gap(170, 499.0).points();
        List<TrackPoint> over = new Track().at(0, 0, 0).run(60, 2.8).gap(170, 501.0).points();

        // when
        TrackFilterSummary accepted = TrackFilter.apply(under, PROPERTIES).summary();
        TrackFilterSummary rejected = TrackFilter.apply(over, PROPERTIES).summary();

        // then
        assertThat(accepted.gapAcceptedCount()).isEqualTo(1);
        assertThat(rejected.gapRejectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("직전 속도는 정지 구간을 빼고 실제로 뛴 칸으로만 낸다")
    void referenceSpeedExcludesStops() {
        // given -> 2.8m/s로 60초, 60초 제자리, 100초 비었다가 300m(3m/s) 앞
        //          정지를 섞으면 직전 속도가 약 1.9m/s로 떨어져(상한 2.5m/s) 거부된다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(60, 2.8)
                .jitter(60, 1.0)
                .gap(100, 300.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().stopCount()).isEqualTo(1);
        assertThat(filtered.summary().gapAcceptedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("출발 직후 관측 안 된 칸은 고정 상한으로 판정한다")
    void gapRightAfterStartUsesFallbackSpeed() {
        // given -> 첫 칸부터 30초 비었다. 150m(5m/s)는 7m/s 아래, 300m(10m/s)는 위
        List<TrackPoint> slow = new Track().at(0, 0, 0).gap(30, 150.0).run(10, 3.0).points();
        List<TrackPoint> fast = new Track().at(0, 0, 0).gap(30, 300.0).run(10, 3.0).points();

        // when
        TrackFilterSummary accepted = TrackFilter.apply(slow, PROPERTIES).summary();
        TrackFilterSummary rejected = TrackFilter.apply(fast, PROPERTIES).summary();

        // then
        assertThat(accepted.gapAcceptedCount()).isEqualTo(1);
        assertThat(rejected.gapRejectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("튀었다 돌아온 점은 점째로 빠지고 앞뒤가 바로 이어진다")
    void spikeAndBackPointIsDropped() {
        // given -> 3m/s로 달리다 한 점만 동쪽으로 200m 튄다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 3.0)
                .at(93, 200, 31)                    // 튄 점
                .at(96, 0, 32)
                .run(30, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().spikePointCount()).isEqualTo(1);
        assertThat(filtered.points()).hasSize(points.size() - 1);
        assertThat(filtered.totalMeters()).isCloseTo(186.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(62.0);
    }

    @Test
    @DisplayName("튄 자리에서 이어 가면 튄 칸 하나만 거리·시간 모두 빠진다")
    void jumpAndStayEdgeIsRejected() {
        // given -> 3m/s로 달리다 1초 만에 500m 옮긴 뒤 그 자리에서 계속 달린다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 3.0)
                .gap(1, 500.0)
                .run(30, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().jumpEdgeCount()).isEqualTo(1);
        assertThat(filtered.points()).hasSize(points.size());
        assertThat(filtered.totalMeters()).isCloseTo(180.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(60.0);
    }

    @Test
    @DisplayName("같은 초에 찍힌 두 점은 1초 간격으로 보고 튐으로 판정하지 않는다")
    void sameSecondPointsAreNotJump() {
        // given -> 같은 초에 3m 떨어진 두 점
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(10, 3.0)
                .at(33, 0, 10)
                .run(10, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().jumpEdgeCount()).isZero();
        assertThat(filtered.summary().spikePointCount()).isZero();
    }

    @Test
    @DisplayName("시계가 뒤로 간 칸은 거리·시간 모두 빠진다")
    void backwardClockEdgeIsRejected() {
        // given -> 30초 달린 뒤 다음 점의 시각이 5초 뒤로 간다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 3.0)
                .at(93, 0, 25)
                .run(10, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then -> 거리만 인정하고 시간을 0으로 두면 그 칸 페이스가 0이 된다
        assertThat(filtered.totalMeters()).isCloseTo(120.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(40.0);
    }

    @Test
    @DisplayName("한 점만 시계가 미래로 튀었다 돌아오면 그 점으로 드나드는 두 칸만 빠진다")
    void singleClockJumpDropsOnlyItsEdges() {
        // given -> 31번째 점만 시각이 이틀 뒤다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 3.0)
                .at(93, 0, 30 + 2 * 24 * 3_600)
                .at(96, 0, 32)
                .run(10, 3.0)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.totalMeters()).isCloseTo(120.0, within(TOLERANCE_METERS));
        assertThat(totalSeconds(filtered)).isEqualTo(40.0);
    }

    @Test
    @DisplayName("정확도가 상한보다 나쁜 점은 계산에서 빠진다")
    void inaccuratePointIsDropped() {
        // given -> 가운데 한 점의 정확도가 80m
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(10, 3.0)
                .at(33, 30, 11, 80.0)
                .at(36, 0, 12).run(9, 3.0)          // 원래 경로로 돌아와 이어 달린다
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        assertThat(filtered.summary().accuracyDroppedCount()).isEqualTo(1);
        assertThat(filtered.points()).hasSize(points.size() - 1);
        assertThat(filtered.totalMeters()).isCloseTo(63.0, within(TOLERANCE_METERS));
    }

    @Test
    @DisplayName("정확도가 나빠도 반경이 최대 반경에서 잘려 달리는 사람은 정지로 잡히지 않는다")
    void inaccurateRunIsNotStop() {
        // given -> 정확도 30m로 3m/s 달리기 — 반경을 자르지 않으면 10초 넘게 반경 안에 머문다
        Track track = new Track().at(0, 0, 0, 30.0);
        for (int second = 1; second <= 60; second++) {
            track.at(second * 3.0, 0, second, 30.0);
        }

        // when
        FilteredTrack filtered = TrackFilter.apply(track.points(), PROPERTIES);

        // then
        assertThat(filtered.summary().stopCount()).isZero();
        assertThat(filtered.summary().radiusClampedCount()).isPositive();
        assertThat(filtered.totalMeters()).isCloseTo(180.0, within(TOLERANCE_METERS));
    }

    @Test
    @DisplayName("단계별로 뺀 거리를 더하면 원본 거리와 필터 거리의 차이다")
    void removedMetersAddUp() {
        // given -> 정확도 나쁜 점, 튄 점, 정지, 택시를 한 트랙에 섞는다
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 2.8)
                .at(87, 20, 31, 80.0)               // 정확도 나쁜 점
                .at(89.8, 0, 32).run(9, 2.8)
                .at(115, 200, 42)                   // 튄 점
                .at(118, 0, 43)
                .run(10, 2.8)
                .jitter(30, 1.0)
                .run(10, 2.8)
                .gap(60, 400.0)                     // 택시
                .run(10, 2.8)
                .points();

        // when
        TrackFilterSummary summary = TrackFilter.apply(points, PROPERTIES).summary();

        // then
        double removed = summary.accuracyRemovedMeters() + summary.spikeRemovedMeters()
                + summary.gapRejectedMeters() + summary.stopRemovedMeters();
        assertThat(summary.accuracyDroppedCount()).isEqualTo(1);
        assertThat(summary.spikePointCount()).isEqualTo(1);
        assertThat(summary.stopCount()).isEqualTo(1);
        assertThat(summary.gapRejectedCount()).isEqualTo(1);
        assertThat(summary.rawMeters() - summary.filteredMeters()).isCloseTo(removed, within(1e-6));
    }

    @Test
    @DisplayName("누적 거리·시간은 줄지 않고 필터 거리는 원본을 넘지 않는다")
    void cumulativeArraysNeverDecrease() {
        // given
        List<TrackPoint> points = new Track()
                .at(0, 0, 0).run(30, 2.8).jitter(20, 1.0).run(30, 2.8)
                .gap(60, 400.0).run(20, 2.8)
                .points();

        // when
        FilteredTrack filtered = TrackFilter.apply(points, PROPERTIES);

        // then
        double[] meters = filtered.cumulativeMeters();
        double[] seconds = filtered.cumulativeMovingSeconds();
        for (int i = 1; i < meters.length; i++) {
            assertThat(meters[i]).isGreaterThanOrEqualTo(meters[i - 1]);
            assertThat(seconds[i]).isGreaterThanOrEqualTo(seconds[i - 1]);
        }
        assertThat(filtered.totalMeters()).isLessThanOrEqualTo(pathMeters(points));
    }

    @Test
    @DisplayName("빈 트랙과 한 점짜리 트랙은 거리 0으로 끝난다")
    void emptyAndSinglePointTracks() {
        // given
        List<TrackPoint> single = new Track().at(0, 0, 0).points();

        // when
        FilteredTrack empty = TrackFilter.apply(List.of(), PROPERTIES);
        FilteredTrack one = TrackFilter.apply(single, PROPERTIES);

        // then
        assertThat(empty.totalMeters()).isZero();
        assertThat(one.totalMeters()).isZero();
        assertThat(one.points()).hasSize(1);
    }
}
