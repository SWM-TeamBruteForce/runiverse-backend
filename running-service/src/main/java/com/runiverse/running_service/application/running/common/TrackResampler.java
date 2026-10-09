package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 실측 트랙을 고정 거리 경계점으로 다시 표본화한다.
// 결과가 곧 route_polyline이자 구간 경계라 다운샘플 단계가 따로 없다.
public final class TrackResampler {

    private TrackResampler() {
    }

    // 필터가 0으로 한 칸 위에는 누적 거리가 늘지 않아 경계점이 찍히지 않는다 —
    // 정지·거부한 구간은 앞뒤 경계점 사이 직선으로 건너뛴다
    public static List<BoundaryPoint> resample(FilteredTrack track, int targetDistanceMeters,
                                               int intervalMeters) {
        List<TrackPoint> points = track.points();
        double[] cumulative = track.cumulativeMeters();
        double[] movingSeconds = track.cumulativeMovingSeconds();
        if (points.size() < 2) {
            return List.of();
        }
        // 목표를 넘겼으면 목표에서 끊고, 못 미쳤으면 마지막으로 완성한 경계까지만 쓴다.
        // 그래서 총거리가 항상 intervalMeters의 배수가 되고 구간 duration의 합과 어긋나지 않는다
        double reached = Math.min(cumulative[cumulative.length - 1], targetDistanceMeters);
        int lastBoundary = (int) (reached / intervalMeters) * intervalMeters;
        if (lastBoundary < intervalMeters) {
            return List.of();   // 한 구간도 못 채웠다 — 기록 없이 상태만 확정하는 경로
        }
        List<BoundaryPoint> boundaries = new ArrayList<>(lastBoundary / intervalMeters + 1);
        int cursor = 1;   // 누적 배열이 단조 증가라 앞으로만 훑으면 된다 — 전체가 O(점 수 + 경계 수)
        for (int distance = 0; distance <= lastBoundary; distance += intervalMeters) {
            // 누적 거리가 경계 이상인 첫 점을 찾는다 — 경계가 정지 구간에 정확히 걸려도
            // 길이 0인 칸이 아니라 정지가 시작되는 점에 붙는다
            while (cursor < cumulative.length - 1 && cumulative[cursor] < distance) {
                cursor++;
            }
            boundaries.add(interpolate(points, cumulative, movingSeconds, cursor, distance));
        }
        return boundaries;
    }

    private static BoundaryPoint interpolate(List<TrackPoint> points, double[] cumulative,
                                             double[] movingSeconds, int cursor, int distance) {
        TrackPoint from = points.get(cursor - 1);
        TrackPoint to = points.get(cursor);
        double span = cumulative[cursor] - cumulative[cursor - 1];
        // 제자리에 머문 구간이면 나눌 값이 없다 — 앞 점을 그대로 쓴다
        double ratio = span <= 0 ? 0 : (distance - cumulative[cursor - 1]) / span;
        return new BoundaryPoint(
                distance,
                from.latitude() + (to.latitude() - from.latitude()) * ratio,
                from.longitude() + (to.longitude() - from.longitude()) * ratio,
                interpolateTime(from.recordedAt(), to.recordedAt(), ratio),
                // 거리와 같은 비율로 나눈다 — 인정한 칸에서는 거리와 시간이 함께 늘기 때문이다
                movingSeconds[cursor - 1] + (movingSeconds[cursor] - movingSeconds[cursor - 1]) * ratio,
                cursor - 1);
    }

    private static LocalDateTime interpolateTime(LocalDateTime from, LocalDateTime to,
                                                 double ratio) {
        long spanNanos = Duration.between(from, to).toNanos();
        return from.plus(Duration.ofNanos((long) (spanNanos * ratio)));
    }
}
