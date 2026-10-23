package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.location.RunningDistanceAccumulation;
import com.runiverse.running_service.application.running.command.location.RunningDistanceAccumulator;
import com.runiverse.running_service.application.running.common.TrackDistance;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.runiverse.running_service.support.TrackFilterFixtures.DEFAULT_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("러닝 누적 거리 누적기 단위 테스트")
class RunningDistanceAccumulatorTest {

    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    // 위도 1e-5도가 약 1.11m다 — 의심점 반경(5~8m) 안팎을 m 단위로 맞춘다
    private static final double METER_IN_LATITUDE = 0.00001 / 1.11;
    // 정지 반경 하한(5m)보다 작은 정확도 — 반경이 5m로 잡힌다
    private static final double GOOD_ACCURACY = 4.2;
    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 10, 13, 7, 0);

    // 기준점에서 북쪽으로 meters만큼 떨어진 좌표
    private static TrackPoint point(long sequence, double meters, double accuracyMeters) {
        return point(sequence, meters, accuracyMeters, 0);
    }

    private static TrackPoint point(long sequence, double meters, double accuracyMeters, int pauseSegment) {
        return new TrackPoint(
                sequence,
                BASE_LATITUDE + meters * METER_IN_LATITUDE,
                BASE_LONGITUDE,
                38.5, accuracyMeters, 2.8, 0.0, 170, 357, RECORDED_AT.plusSeconds(sequence), pauseSegment);
    }

    private static TrackPoint point(long sequence, double meters) {
        return point(sequence, meters, GOOD_ACCURACY);
    }

    // 의심점이 기준점에 있는 누적 — 직전 배치까지 순번 0을 받았다
    private static RunningDistance anchoredAtOrigin() {
        TrackPoint origin = point(0, 0);
        return new RunningDistance(0, 0, 0, origin.latitude(), origin.longitude(), 357,
                origin.latitude(), origin.longitude());
    }

    private static RunningDistanceAccumulation accumulate(RunningDistance from, TrackPoint... points) {
        return RunningDistanceAccumulator.accumulate(from, List.of(points), DEFAULT_PROPERTIES);
    }

    @Test
    @DisplayName("첫 관측 좌표가 첫 의심점이 되고, 옮겨진 것으로 보지 않는다")
    void firstObservedPointBecomesAnchorWithoutMoving() {
        // when
        TrackPoint first = point(0, 0);
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), first);

        // then -> 출발선에 선 참가자가 여기서 달리는 중이 되면 출발선 콤보가 붙는다
        assertThat(result.observed()).isTrue();
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(first.latitude());
        assertThat(result.distance().anchorLongitude()).isEqualTo(first.longitude());
    }

    @Test
    @DisplayName("의심점 반경 밖 좌표가 오면 의심점을 그 좌표로 옮긴다")
    void movesAnchorWhenPointLeavesRadius() {
        // when -> 11m는 반경(5m) 밖이다
        TrackPoint away = point(1, 11);
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), away);

        // then
        assertThat(result.observed()).isTrue();
        assertThat(result.moved()).isTrue();
        assertThat(result.distance().anchorLatitude()).isEqualTo(away.latitude());
    }

    @Test
    @DisplayName("의심점 반경 안에 머물면 의심점을 그대로 둔다")
    void keepsAnchorWhilePointsStayInsideRadius() {
        // when -> 신호 대기 중 흔들림 — 3m 안팎을 오간다
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(),
                point(1, 3), point(2, -2), point(3, 3));

        // then -> 관측은 했지만 움직이지 않았다
        RunningDistance origin = anchoredAtOrigin();
        assertThat(result.observed()).isTrue();
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(origin.anchorLatitude());
        // 흔들림은 지금처럼 누적 거리에는 섞인다 — 기록은 트랙 필터가 바로잡는다
        assertThat(result.distance().meters()).isGreaterThan(0);
    }

    @Test
    @DisplayName("반경은 좌표 정확도를 정지 반경 하한·상한 사이로 자른 값이다")
    void radiusIsAccuracyClampedToStopRadius() {
        // when -> 7m 떨어진 좌표. 정확도 4.2m면 반경이 하한 5m라 밖이고,
        // 정확도 20m면 반경이 상한 8m라 안이다
        RunningDistanceAccumulation precise = accumulate(anchoredAtOrigin(), point(1, 7, GOOD_ACCURACY));
        RunningDistanceAccumulation coarse = accumulate(anchoredAtOrigin(), point(1, 7, 20.0));

        // then -> 상한이 없으면 정확도가 나쁜 곳에서 달리는 사람도 서 있는 것으로 잡힌다
        assertThat(precise.moved()).isTrue();
        assertThat(coarse.moved()).isFalse();
    }

    @Test
    @DisplayName("정확도가 상한보다 나쁜 좌표는 관측이 아니다 — 의심점을 옮기지 않고 거리에는 더한다")
    void inaccuratePointIsNotObservation() {
        // when -> 터널에서 정확도 60m로 튄 좌표가 30m 밖에 찍혔다
        TrackPoint inaccurate = point(1, 30, 60.0);
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), inaccurate);

        // then -> 튄 좌표로 의심점이 옮겨지면 서 있는 사람이 달리는 것으로 보인다
        assertThat(result.observed()).isFalse();
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(anchoredAtOrigin().anchorLatitude());
        // 거리 계산은 바꾸지 않는다
        assertThat(result.distance().meters())
                .isEqualTo(TrackDistance.between(point(0, 0), inaccurate),
                        org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("정확도가 나쁜 좌표만 받았으면 첫 의심점도 만들지 않는다")
    void doesNotCreateAnchorFromInaccuratePoints() {
        // when
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), point(0, 0, 60.0));

        // then
        assertThat(result.observed()).isFalse();
        assertThat(result.distance().anchorLatitude()).isNull();
        assertThat(result.distance().anchorLongitude()).isNull();
    }

    @Test
    @DisplayName("재전송분만 온 배치는 관측이 아니다")
    void resentBatchIsNotObservation() {
        // when -> 재연결 직후 클라가 이미 받은 순번 0을 다시 보냈다
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), point(0, 20));

        // then -> 이미 반영한 좌표로 움직였다고 보면 재연결만으로 콤보가 쌓인다
        assertThat(result.observed()).isFalse();
        assertThat(result.moved()).isFalse();
        assertThat(result.distance()).isEqualTo(anchoredAtOrigin());
    }

    @Test
    @DisplayName("한 배치 안에서 여러 번 벗어나면 의심점은 마지막으로 벗어난 좌표다")
    void anchorFollowsLastExitWithinBatch() {
        // when -> 뒤섞여 와도 순번 순서로 훑는다
        TrackPoint last = point(3, 33);
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(),
                last, point(1, 11), point(2, 22));

        // then
        assertThat(result.moved()).isTrue();
        assertThat(result.distance().anchorLatitude()).isEqualTo(last.latitude());
    }

    @Test
    @DisplayName("의심점이 아직 없는 누적이면 다음 관측 좌표가 첫 의심점이 된다")
    void distanceWithoutAnchorStartsFromNextObservation() {
        // given -> 지금까지 정확도가 나쁜 좌표만 받아 의심점이 없다
        TrackPoint previous = point(0, 0);
        RunningDistance withoutAnchor = new RunningDistance(500, 0, 0, previous.latitude(), previous.longitude(),
                357, null, null);

        // when
        TrackPoint next = point(1, 11);
        RunningDistanceAccumulation result = accumulate(withoutAnchor, next);

        // then -> 이번 배치는 기준을 세울 뿐이고, 다음 배치부터 움직임을 판정한다
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(next.latitude());
    }

    @Test
    @DisplayName("거리에 반영한 마지막 좌표의 일시정지 구간 번호를 남긴다")
    void keepsPauseSegmentOfLastAppliedPoint() {
        // given -> 두 좌표 사이에서 일시정지 버튼을 한 번 눌렀다
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(),
                point(0, 0, GOOD_ACCURACY, 0), point(1, 3, GOOD_ACCURACY, 1));

        // then -> 다음 배치가 이 번호보다 작으면 거부된다
        assertThat(result.distance().lastPauseSegment()).isEqualTo(1);
    }

    @Test
    @DisplayName("재전송분만 온 배치는 일시정지 구간 번호를 바꾸지 않는다")
    void resentBatchKeepsPauseSegment() {
        // given -> 순번 0까지 반영했고 그때 번호는 2였다
        TrackPoint origin = point(0, 0, GOOD_ACCURACY, 2);
        RunningDistance stored = new RunningDistance(0, 0, 2, origin.latitude(), origin.longitude(), 357,
                origin.latitude(), origin.longitude());

        // when -> 같은 순번이 다른 번호로 다시 와도 거리에 반영하지 않는다
        RunningDistanceAccumulation result = accumulate(stored, point(0, 0, GOOD_ACCURACY, 0));

        // then
        assertThat(result.distance().lastPauseSegment()).isEqualTo(2);
    }

    // 일시정지 구간 번호를 붙인 좌표 — 정확도는 좋은 값이다
    private static TrackPoint paused(long sequence, double meters, int pauseSegment) {
        return point(sequence, meters, GOOD_ACCURACY, pauseSegment);
    }

    @Test
    @DisplayName("배치 안에서 일시정지 구간 번호가 바뀐 칸은 누적에 더하지 않는다")
    void skipsPauseBoundaryWithinBatch() {
        // given -> 0m → 10m를 달리고, 일시정지한 채 200m를 옮겨 재개한 뒤 10m를 더 달렸다
        TrackPoint a = paused(0, 0, 0);
        TrackPoint b = paused(1, 10, 0);
        TrackPoint c = paused(2, 210, 1);
        TrackPoint d = paused(3, 220, 1);

        // when
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), a, b, c, d);

        // then -> 일시정지 중 이동(200m)이 섞이면 상대 화면이 튀고 목표에 일찍 닿는다
        double expected = TrackDistance.between(a, b) + TrackDistance.between(c, d);
        assertThat(result.distance().meters()).isCloseTo(expected, within(1e-6));
    }

    @Test
    @DisplayName("직전 배치 마지막 좌표와 번호가 다르면 배치 사이 칸도 더하지 않는다")
    void skipsPauseBoundaryAcrossBatches() {
        // given -> 직전 배치는 순번 0, 번호 0에서 끝났다
        TrackPoint origin = paused(0, 0, 0);
        RunningDistance stored = new RunningDistance(0, 0, 0, origin.latitude(), origin.longitude(), 357,
                origin.latitude(), origin.longitude());
        TrackPoint resumed = paused(1, 200, 1);
        TrackPoint next = paused(2, 210, 1);

        // when -> 이번 배치의 첫 좌표가 일시정지 뒤 재개 지점이다
        RunningDistanceAccumulation result = accumulate(stored, resumed, next);

        // then -> 재개 지점부터 이어 센다
        assertThat(result.distance().meters()).isCloseTo(TrackDistance.between(resumed, next), within(1e-6));
        assertThat(result.distance().lastPauseSegment()).isEqualTo(1);
    }

    @Test
    @DisplayName("경계를 건너뛴 뒤의 마지막 좌표는 재개 지점이다")
    void movesLastPointToResumedPosition() {
        // given -> 경계 칸 하나만 있는 배치
        TrackPoint before = paused(0, 0, 0);
        TrackPoint resumed = paused(1, 200, 1);

        // when
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), before, resumed);

        // then -> 다음 배치의 첫 칸이 일시정지 전 자리에서 이어지면 200m가 다시 들어온다
        assertThat(result.distance().meters()).isZero();
        assertThat(result.distance().lastLatitude()).isEqualTo(resumed.latitude());
        assertThat(result.distance().lastLongitude()).isEqualTo(resumed.longitude());
    }

    @Test
    @DisplayName("번호가 같으면 지금처럼 이어서 더한다")
    void addsWithinSameSegment() {
        // given -> 일시정지 버튼을 한 번 누른 뒤로 계속 같은 구간이다
        TrackPoint a = paused(0, 0, 2);
        TrackPoint b = paused(1, 10, 2);
        TrackPoint c = paused(2, 20, 2);

        // when
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), a, b, c);

        // then
        assertThat(result.distance().meters())
                .isCloseTo(TrackDistance.between(a, b) + TrackDistance.between(b, c), within(1e-6));
    }

    @Test
    @DisplayName("일시정지 경계를 넘으면 의심점을 비우고 경계 뒤 첫 관측을 이동으로 보지 않는다")
    void clearsAnchorAtPauseBoundary() {
        // given -> 의심점은 출발점이다. 일시정지한 채 200m 옮겨 재개했다
        TrackPoint resumed = paused(1, 200, 1);

        // when
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), resumed);

        // then -> 의심점 반경 밖이지만 옮겨진 것이 아니다 — 일시정지 중 이동은 달린 것이 아니다
        assertThat(result.pauseCrossed()).isTrue();
        assertThat(result.moved()).isFalse();
        assertThat(result.observed()).isTrue();
        assertThat(result.distance().anchorLatitude()).isEqualTo(resumed.latitude());
        assertThat(result.distance().anchorLongitude()).isEqualTo(resumed.longitude());
    }

    @Test
    @DisplayName("경계 뒤 첫 좌표가 관측이 아니면 의심점을 비운 채 저장한다")
    void keepsAnchorEmptyWhenFirstPointAfterBoundaryIsNotObserved() {
        // given -> 재개 첫 좌표의 정확도가 상한보다 나쁘다
        TrackPoint inaccurate = point(1, 200, 80.0, 1);

        // when
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), inaccurate);

        // then -> 옛 의심점이 남으면 다음 관측이 반경 밖이라 옮겨진 것으로 잡힌다
        assertThat(result.pauseCrossed()).isTrue();
        assertThat(result.observed()).isFalse();
        assertThat(result.distance().anchorLatitude()).isNull();
        assertThat(result.distance().anchorLongitude()).isNull();
    }

    @Test
    @DisplayName("비운 의심점은 다음 배치의 첫 관측이 채우고 그것도 이동으로 보지 않는다")
    void nextBatchFillsEmptiedAnchorWithoutMoving() {
        // given -> 직전 배치가 경계를 넘었고 관측이 없어 의심점이 비어 있다
        TrackPoint inaccurate = point(1, 200, 80.0, 1);
        RunningDistance emptied = accumulate(anchoredAtOrigin(), inaccurate).distance();

        // when -> 같은 일시정지 구간 안에서 관측이 들어온다
        TrackPoint observed = paused(2, 203, 1);
        RunningDistanceAccumulation result = accumulate(emptied, observed);

        // then -> 경계는 이미 지났다
        assertThat(result.pauseCrossed()).isFalse();
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(observed.latitude());
    }

    @Test
    @DisplayName("러닝의 첫 좌표는 번호가 0이 아니어도 경계가 아니다")
    void firstPointOfRunIsNotBoundary() {
        // given -> 첫 배치를 받기 전에 일시정지를 눌렀다 — 앞 좌표가 없어 칸이 없다
        RunningDistanceAccumulation result = accumulate(RunningDistance.empty(), paused(0, 0, 1));

        // then
        assertThat(result.pauseCrossed()).isFalse();
    }

    @Test
    @DisplayName("같은 일시정지 구간 안이면 경계를 넘은 것이 아니다")
    void sameSegmentIsNotBoundary() {
        // when
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), paused(1, 11, 0));

        // then -> 지금처럼 반경 밖이면 옮겨진다
        assertThat(result.pauseCrossed()).isFalse();
        assertThat(result.moved()).isTrue();
    }
}
