package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.common.RouteTimeline;
import com.runiverse.running_service.application.running.port.out.RecordedRoute;
import com.runiverse.running_service.application.running.port.out.RecordedSplitTime;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("기록 경로 시각표 단위 테스트")
class RouteTimelineTest {

    private static final UUID USER = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 7, 0, 0);
    // 경계점 6개(p0~p5)를 3초 간격으로 지났다 — 위도로 몇 번째 점인지 구분한다
    private static final int POINT_COUNT = 6;
    private static final int SECONDS_PER_POINT = 3;

    private static double latitudeOf(int index) {
        return 37.5 + index * 0.001;
    }

    private static RecordedRoute route(int pointCount, int splitCount) {
        List<RecordedSplitTime> splits = new ArrayList<>();
        for (int i = 0; i < splitCount; i++) {
            splits.add(split(1, i, i + 1, i));
        }
        return new RecordedRoute(USER, List.of(polyline(0, pointCount)), splits);
    }

    // p_from ~ p_(toExclusive-1)를 한 조각으로 인코딩한다
    private static String polyline(int from, int toExclusive) {
        List<BoundaryPoint> points = new ArrayList<>();
        for (int i = from; i < toExclusive; i++) {
            points.add(new BoundaryPoint(i * 10, latitudeOf(i), 127.0,
                    START.plusSeconds((long) i * SECONDS_PER_POINT), i * SECONDS_PER_POINT, i));
        }
        return PolylineEncoder.encode(points);
    }

    // 구간 number+1은 p_number → p_(number+1)을 지났다. 경로 범위는 segmentNumber 조각 안의 번호다
    private static RecordedSplitTime split(int segmentNumber, int routeStartIndex, int routeEndIndex,
                                           int number) {
        return new RecordedSplitTime(segmentNumber, routeStartIndex, routeEndIndex,
                START.plusSeconds((long) number * SECONDS_PER_POINT),
                START.plusSeconds((long) (number + 1) * SECONDS_PER_POINT));
    }

    private static RouteTimeline timeline() {
        return RouteTimeline.of(route(POINT_COUNT, POINT_COUNT - 1)).orElseThrow();
    }

    private static void assertPoint(ComboPoint point, int expectedIndex) {
        assertThat(point.latitude()).isCloseTo(latitudeOf(expectedIndex), within(1e-5));
    }

    @Test
    @DisplayName("경계점을 지난 시각과 같으면 그 점이다")
    void picksExactPoint() {
        assertPoint(timeline().nearest(START.plusSeconds(9)), 3);
    }

    @Test
    @DisplayName("두 경계점 사이면 시각이 더 가까운 점이다")
    void picksCloserPoint() {
        // when -> 0:07은 p2(0:06)에 1초, p3(0:09)에 2초
        assertPoint(timeline().nearest(START.plusSeconds(7)), 2);
        // 0:08은 p3에 더 가깝다
        assertPoint(timeline().nearest(START.plusSeconds(8)), 3);
    }

    @Test
    @DisplayName("똑같이 가까우면 앞의 점이다")
    void picksEarlierOnTie() {
        // when -> 0:07.5는 p2·p3에서 1.5초씩
        assertPoint(timeline().nearest(START.plusSeconds(7).plusNanos(500_000_000)), 2);
    }

    @Test
    @DisplayName("경로보다 이르거나 늦으면 첫 점·끝 점이다")
    void clampsToEnds() {
        assertPoint(timeline().nearest(START.minusSeconds(5)), 0);
        assertPoint(timeline().nearest(START.plusSeconds(60)), POINT_COUNT - 1);
    }

    @Test
    @DisplayName("구간이 경로를 빈틈없이 덮지 못하면 점을 고를 수 없다")
    void rejectsUncoveredRoute() {
        // when -> 마지막 경계점의 시각이 없다
        assertThat(RouteTimeline.of(route(POINT_COUNT, POINT_COUNT - 2))).isEmpty();
    }

    @Test
    @DisplayName("구간이 경로 밖 번호를 가리키면 점을 고를 수 없다")
    void rejectsSplitBeyondRoute() {
        // when -> 점은 4개인데 구간은 5번 점까지 가리킨다
        RecordedRoute broken = new RecordedRoute(USER, route(4, 3).segmentPolylines(),
                route(POINT_COUNT, POINT_COUNT - 1).splits());

        // then
        assertThat(RouteTimeline.of(broken)).isEmpty();
    }

    @Test
    @DisplayName("경로가 비어 있으면 점을 고를 수 없다")
    void rejectsEmptyRoute() {
        assertThat(RouteTimeline.of(new RecordedRoute(USER, List.of(), List.of()))).isEmpty();
        assertThat(RouteTimeline.of(new RecordedRoute(USER, List.of(""), List.of()))).isEmpty();
    }

    // p0~p2가 1번 조각, p3~p5가 2번 조각이다. 구간 3(p2→p3)이 끊긴 곳에 걸쳐 2번 조각 [0, 0]이다
    private static RecordedRoute pausedRoute() {
        return new RecordedRoute(USER, List.of(polyline(0, 3), polyline(3, POINT_COUNT)), List.of(
                split(1, 0, 1, 0), split(1, 1, 2, 1),
                split(2, 0, 0, 2),
                split(2, 0, 1, 3), split(2, 1, 2, 4)));
    }

    @Test
    @DisplayName("조각을 이어 경계점 전체로 편다 -> 콤보 점은 조각을 모른다")
    void flattensSegments() {
        // given
        RouteTimeline timeline = RouteTimeline.of(pausedRoute()).orElseThrow();

        // when & then -> 조각이 바뀌어도 시각 순서대로 점을 고른다
        assertPoint(timeline.nearest(START.plusSeconds(6)), 2);
        assertPoint(timeline.nearest(START.plusSeconds(9)), 3);
        assertPoint(timeline.nearest(START.plusSeconds(15)), 5);
    }

    @Test
    @DisplayName("걸친 구간의 시작 시각은 앞 조각의 마지막 점에 붙는다")
    void acrossSplitStartsAtPreviousSegmentEnd() {
        // given -> 1번 조각은 출발점 하나뿐이다. 첫 구간부터 2번 조각에 걸친다([0, 0])
        RecordedRoute route = new RecordedRoute(USER, List.of(polyline(0, 1), polyline(1, POINT_COUNT)),
                List.of(split(2, 0, 0, 0), split(2, 0, 1, 1), split(2, 1, 2, 2),
                        split(2, 2, 3, 3), split(2, 3, 4, 4)));

        // when -> 시작 시각을 p1에 덮어쓰면 p0의 시각이 비어 이 참가자의 콤보 점이 통째로 빠진다
        RouteTimeline timeline = RouteTimeline.of(route).orElseThrow();

        // then
        assertPoint(timeline.nearest(START), 0);
        assertPoint(timeline.nearest(START.plusSeconds(3)), 1);
    }

    @Test
    @DisplayName("구간이 기록에 없는 조각을 가리키면 점을 고를 수 없다")
    void rejectsSplitOfMissingSegment() {
        // given -> 조각은 하나뿐인데 2번 조각을 가리킨다
        RecordedRoute broken = new RecordedRoute(USER, List.of(polyline(0, POINT_COUNT)),
                List.of(split(1, 0, 1, 0), split(2, 0, 0, 1)));

        // then
        assertThat(RouteTimeline.of(broken)).isEmpty();
    }

    @Test
    @DisplayName("구간이 자기 조각 밖 번호를 가리키면 점을 고를 수 없다 -> 이어 붙인 전체로는 맞아도 거부한다")
    void rejectsSplitBeyondItsSegment() {
        // given -> 1번 조각은 p0~p2 셋인데 [2, 3]을 가리킨다. 이으면 p3이 있어 전체 길이로만 보면 통과한다
        RecordedRoute broken = new RecordedRoute(USER, List.of(polyline(0, 3), polyline(3, POINT_COUNT)),
                List.of(split(1, 0, 1, 0), split(1, 1, 2, 1), split(1, 2, 3, 2)));

        // then
        assertThat(RouteTimeline.of(broken)).isEmpty();
    }
}
