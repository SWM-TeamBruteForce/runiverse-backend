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
        return new TrackPoint(
                sequence,
                BASE_LATITUDE + meters * METER_IN_LATITUDE,
                BASE_LONGITUDE,
                38.5, accuracyMeters, 2.8, 0.0, 170, 357, RECORDED_AT.plusSeconds(sequence));
    }

    private static TrackPoint point(long sequence, double meters) {
        return point(sequence, meters, GOOD_ACCURACY);
    }

    // 측정 시각을 따로 정한 좌표 — 일시정지 공백처럼 순번과 초가 어긋나는 칸을 만든다
    private static TrackPoint pointAt(long sequence, double meters, long second) {
        return new TrackPoint(
                sequence,
                BASE_LATITUDE + meters * METER_IN_LATITUDE,
                BASE_LONGITUDE,
                38.5, GOOD_ACCURACY, 2.8, 0.0, 170, 357, RECORDED_AT.plusSeconds(second));
    }

    private static RunningDistanceAccumulation accumulate(RunningDistance from, List<Long> pausedSequences,
                                                          TrackPoint... points) {
        return RunningDistanceAccumulator.accumulate(from, List.of(points), pausedSequences, DEFAULT_PROPERTIES);
    }

    // 의심점이 기준점에 있는 누적 — 직전 배치까지 순번 0을 받았다
    private static RunningDistance anchoredAtOrigin() {
        TrackPoint origin = point(0, 0);
        return new RunningDistance(0, 0, origin.latitude(), origin.longitude(), 357,
                origin.latitude(), origin.longitude(), origin.recordedAt());
    }

    private static RunningDistanceAccumulation accumulate(RunningDistance from, TrackPoint... points) {
        return accumulate(from, List.of(), points);
    }

    @Test
    @DisplayName("일시정지 표시 뒤 공백 칸은 누적 거리에 더하지 않는다")
    void skipsPausedGapEdge() {
        // given -> 순번 0에서 멈추고 60초 뒤 200m 앞에서 재개했다
        TrackPoint resumed = pointAt(1, 200, 60);
        assertThat(accumulate(anchoredAtOrigin(), resumed).distance().meters())
                .isGreaterThan(190);   // 표시가 없으면 재개하는 순간 200m가 튄다

        // when
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), List.of(0L), resumed);

        // then -> 마지막 좌표는 재개한 자리로 옮겨 다음 칸부터 이어 센다
        assertThat(result.distance().meters()).isZero();
        assertThat(result.distance().lastSequence()).isEqualTo(1L);
        assertThat(result.distance().lastRecordedAt()).isEqualTo(resumed.recordedAt());
    }

    @Test
    @DisplayName("멈추기 전 좌표가 늦게 와 공백이 배치 안에 있어도 범위 안이면 뺀다")
    void skipsPausedGapInsideBatch() {
        // when -> 표시는 0인데 1·2가 PAUSE보다 늦게 왔다. 공백은 2→3이다
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), List.of(0L),
                pointAt(1, 3, 1), pointAt(2, 6, 2), pointAt(3, 206, 62));

        // then
        assertThat(result.distance().meters()).isCloseTo(6, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("표시 뒤 범위를 넘어서 시작한 공백은 그대로 더한다")
    void countsGapOutsideWindow() {
        // given -> 표시 0, 공백은 순번 11에서 시작한다(범위 10)
        RunningDistance stored = new RunningDistance(0, 11, pointAt(11, 0, 11).latitude(), BASE_LONGITUDE,
                357, null, null, RECORDED_AT.plusSeconds(11));

        // when
        RunningDistanceAccumulation result = accumulate(stored, List.of(0L), pointAt(12, 200, 71));

        // then -> 일시정지와 무관한 공백이다. 터널 인정 여부는 종료 때 트랙 필터가 가린다
        assertThat(result.distance().meters()).isCloseTo(200, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    @DisplayName("10초 이하로 멈췄다 재개한 칸은 표시가 있어도 더한다")
    void countsShortPauseEdge() {
        // when -> 순번 0에서 멈추고 8초 뒤 20m 앞에서 재개했다
        RunningDistanceAccumulation result = accumulate(anchoredAtOrigin(), List.of(0L), pointAt(1, 20, 8));

        // then
        assertThat(result.distance().meters()).isCloseTo(20, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("재연결로 처음부터 다시 보내도 이미 뺀 공백을 다시 더하지 않는다")
    void resendAfterResumeDoesNotRecountGap() {
        // given -> 순번 0에서 멈췄다가 순번 1(60초 뒤, 200m 앞)에서 재개해 이미 반영했다
        TrackPoint resumed = pointAt(1, 200, 60);
        RunningDistance afterResume = accumulate(anchoredAtOrigin(), List.of(0L), resumed).distance();

        // when -> 재연결한 클라가 0부터 다시 보낸다. 2만 처음 보는 순번이다
        RunningDistanceAccumulation result = accumulate(afterResume, List.of(0L),
                pointAt(0, 0, 0), resumed, pointAt(2, 203, 61));

        // then
        assertThat(result.distance().meters()).isCloseTo(3, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("시각이 없는 옛 누적이면 배치 사이 칸은 판정하지 않고 더한다")
    void legacyDistanceWithoutRecordedAtCountsGap() {
        // given -> 시각을 싣기 전 포맷에서 읽은 누적
        TrackPoint previous = point(0, 0);
        RunningDistance legacy = new RunningDistance(0, 0, previous.latitude(), previous.longitude(),
                357, previous.latitude(), previous.longitude(), null);

        // when
        RunningDistanceAccumulation result = accumulate(legacy, List.of(0L), pointAt(1, 200, 60));

        // then -> 이번 배치가 시각을 남긴다
        assertThat(result.distance().meters()).isGreaterThan(190);
        assertThat(result.distance().lastRecordedAt()).isEqualTo(RECORDED_AT.plusSeconds(60));
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
    @DisplayName("의심점이 없는 옛 누적이면 다음 관측 좌표가 첫 의심점이 된다")
    void legacyDistanceWithoutAnchorStartsFromNextObservation() {
        // given -> 의심점을 싣기 전 포맷에서 읽은 누적
        TrackPoint previous = point(0, 0);
        RunningDistance legacy = new RunningDistance(500, 0, previous.latitude(), previous.longitude(),
                357, null, null, null);

        // when
        TrackPoint next = point(1, 11);
        RunningDistanceAccumulation result = accumulate(legacy, next);

        // then -> 이번 배치는 기준을 세울 뿐이고, 다음 배치부터 움직임을 판정한다
        assertThat(result.moved()).isFalse();
        assertThat(result.distance().anchorLatitude()).isEqualTo(next.latitude());
    }
}
