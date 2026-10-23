package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.FilteredTrack;
import com.runiverse.running_service.application.running.common.RouteSegmenter;
import com.runiverse.running_service.application.running.common.TrackFilter;
import com.runiverse.running_service.application.running.common.TrackResampler;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.runiverse.running_service.support.TrackFilterFixtures.DEFAULT_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("경로 조각 나누기 단위 테스트")
public class RouteSegmenterTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final int INTERVAL = 10;
    private static final int TARGET = 5_000;
    private static final double METERS_PER_DEGREE = 111_320.0;

    // 조각 나누기는 거리·sourceIndex만 본다 — 위치·시각은 구분만 되면 된다
    private static BoundaryPoint boundary(int distanceMeters, int sourceIndex) {
        return new BoundaryPoint(distanceMeters, 37.5 + distanceMeters / METERS_PER_DEGREE, 127.0,
                START.plusSeconds(distanceMeters), distanceMeters, sourceIndex);
    }

    // 끊는 칸 번호만 true인 배열 — 칸 i는 points[i-1] → points[i]다
    private static boolean[] breaksAt(int pointCount, int... edges) {
        boolean[] breaks = new boolean[pointCount];
        for (int edge : edges) {
            breaks[edge] = true;
        }
        return breaks;
    }

    private static List<BoundaryPoint> concat(List<List<BoundaryPoint>> segments) {
        return segments.stream().flatMap(List::stream).toList();
    }

    // 경계점 넷이 칸 1·3·6·8 위에 놓인 트랙 — 점은 0~9 열 개다
    private static final List<BoundaryPoint> BOUNDARIES = List.of(
            boundary(0, 0), boundary(10, 2), boundary(20, 5), boundary(30, 7));
    private static final int POINT_COUNT = 10;

    @Test
    @DisplayName("끊는 칸이 없으면 조각 하나에 경계점이 모두 들어간다")
    void noBreakMakesSingleSegment() {
        // when
        List<List<BoundaryPoint>> segments =
                RouteSegmenter.split(BOUNDARIES, breaksAt(POINT_COUNT));

        // then
        assertThat(segments).containsExactly(BOUNDARIES);
    }

    @Test
    @DisplayName("두 경계점이 놓인 칸 사이에 끊는 칸이 있으면 그 사이에서 나눈다")
    void breakBetweenBoundariesSplits() {
        // given -> 10m 경계점(칸 3)과 20m 경계점(칸 6) 사이의 칸 4
        boolean[] breaks = breaksAt(POINT_COUNT, 4);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then
        assertThat(segments).containsExactly(
                List.of(BOUNDARIES.get(0), BOUNDARIES.get(1)),
                List.of(BOUNDARIES.get(2), BOUNDARIES.get(3)));
    }

    @Test
    @DisplayName("끊는 칸이 여럿이면 그만큼 조각이 늘어난다")
    void multipleBreaksMakeMoreSegments() {
        // given -> 0m~10m 사이(칸 2), 20m~30m 사이(칸 7)
        boolean[] breaks = breaksAt(POINT_COUNT, 2, 7);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then
        assertThat(segments).containsExactly(
                List.of(BOUNDARIES.get(0)),
                List.of(BOUNDARIES.get(1), BOUNDARIES.get(2)),
                List.of(BOUNDARIES.get(3)));
    }

    @Test
    @DisplayName("한 경계점 사이에 끊는 칸이 여럿이어도 한 번만 나눈다")
    void consecutiveBreaksSplitOnce() {
        // given -> 10m 경계점(칸 3)과 20m 경계점(칸 6) 사이 칸 4·5가 모두 끊는 칸이다.
        // 경계점이 놓인 칸은 누적 거리가 늘어난 칸이라 끊는 칸일 수 없다
        boolean[] breaks = breaksAt(POINT_COUNT, 4, 5);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then -> 빈 조각이 생기면 클라가 그릴 것 없는 배열을 받는다
        assertThat(segments).hasSize(2);
        assertThat(segments).allSatisfy(segment -> assertThat(segment).isNotEmpty());
    }

    @Test
    @DisplayName("첫 칸이 끊는 칸이면 0m 경계점 혼자 첫 조각이다")
    void breakOnFirstEdgeIsolatesOrigin() {
        // given -> 첫 칸이 거부되면 0m 경계점은 그 칸의 시작점(sourceIndex 0)에 붙는다
        boolean[] breaks = breaksAt(POINT_COUNT, 1);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then -> 출발점과 재개한 자리를 이으면 옮긴 길이 그려진다
        assertThat(segments).containsExactly(
                List.of(BOUNDARIES.get(0)),
                List.of(BOUNDARIES.get(1), BOUNDARIES.get(2), BOUNDARIES.get(3)));
    }

    @Test
    @DisplayName("마지막 경계점 뒤의 끊는 칸은 조각을 나누지 않는다")
    void breakAfterLastBoundaryIsIgnored() {
        // given -> 30m 경계점은 칸 8 위다. 칸 9는 기록 밖이다
        boolean[] breaks = breaksAt(POINT_COUNT, 9);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then -> 뒤에 그릴 경계점이 없는데 빈 조각이 생기면 안 된다
        assertThat(segments).containsExactly(BOUNDARIES);
    }

    @Test
    @DisplayName("조각을 순서대로 이으면 원래 경계점 목록과 같다")
    void segmentsConcatenateToOriginal() {
        // given
        boolean[] breaks = breaksAt(POINT_COUNT, 2, 4, 7);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(BOUNDARIES, breaks);

        // then -> 점이 빠지거나 겹치면 구간 인덱스(route_polyline 기준)와 어긋난다
        assertThat(concat(segments)).containsExactlyElementsOf(BOUNDARIES);
    }

    @Test
    @DisplayName("경계점이 없으면 조각도 없다")
    void emptyBoundariesMakeNoSegment() {
        // when & then
        assertThat(RouteSegmenter.split(List.of(), new boolean[0])).isEmpty();
    }

    @Test
    @DisplayName("필터가 거부한 택시 구간을 사이에 두고 실제 트랙이 두 조각으로 나뉜다")
    void rejectedGapSplitsRealTrack() {
        // given -> 2.8m/s로 600초, 60초 만에 400m 앞(택시), 다시 2.8m/s로 600초
        List<TrackPoint> points = new ArrayList<>();
        double north = 0;
        long second = 0;
        for (int i = 0; i <= 600; i++, north += 2.8, second++) {
            points.add(point(points.size(), north, second));
        }
        north += 400.0 - 2.8;
        second += 60 - 1;
        for (int i = 0; i <= 600; i++, north += 2.8, second++) {
            points.add(point(points.size(), north, second));
        }
        FilteredTrack filtered = TrackFilter.apply(points, DEFAULT_PROPERTIES);
        List<BoundaryPoint> boundaries = TrackResampler.resample(filtered, TARGET, INTERVAL);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(boundaries, filtered.breakEdges());

        // then -> 앞 조각의 끝은 택시를 타기 전, 뒤 조각의 시작은 내린 뒤다
        assertThat(filtered.summary().gapRejectedCount()).isEqualTo(1);
        assertThat(segments).hasSize(2);
        assertThat(concat(segments)).containsExactlyElementsOf(boundaries);
        BoundaryPoint beforeTaxi = segments.get(0).getLast();
        BoundaryPoint afterTaxi = segments.get(1).getFirst();
        assertThat(beforeTaxi.sourceIndex()).isLessThan(601);
        assertThat(afterTaxi.sourceIndex()).isGreaterThanOrEqualTo(601);
    }

    @Test
    @DisplayName("정지 구간이 있는 실제 트랙은 나뉘지 않는다")
    void stopKeepsRealTrackInOneSegment() {
        // given -> 2.8m/s로 300초, 60초 제자리, 다시 2.8m/s로 300초
        List<TrackPoint> points = new ArrayList<>();
        double north = 0;
        long second = 0;
        for (int i = 0; i <= 300; i++, north += 2.8, second++) {
            points.add(point(points.size(), north, second));
        }
        north -= 2.8;
        for (int i = 0; i < 60; i++, second++) {
            points.add(point(points.size(), north + (i % 2 == 0 ? 1.0 : -1.0), second));
        }
        for (int i = 0; i <= 300; i++, north += 2.8, second++) {
            points.add(point(points.size(), north, second));
        }
        FilteredTrack filtered = TrackFilter.apply(points, DEFAULT_PROPERTIES);
        List<BoundaryPoint> boundaries = TrackResampler.resample(filtered, TARGET, INTERVAL);

        // when
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(boundaries, filtered.breakEdges());

        // then
        assertThat(filtered.summary().stopCount()).isPositive();
        assertThat(segments).containsExactly(boundaries);
    }

    // 출발점에서 북쪽으로 north 미터, 출발부터 second초
    private static TrackPoint point(long sequence, double north, long second) {
        return new TrackPoint(sequence, 37.5 + north / METERS_PER_DEGREE, 127.0,
                null, 5.0, null, null, null, null, START.plusSeconds(second), 0);
    }
}
