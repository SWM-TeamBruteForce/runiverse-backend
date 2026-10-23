package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.RecordedRoute;
import com.runiverse.running_service.application.running.port.out.RecordedSplitTime;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 기록 경로의 경계점마다 시각을 붙인 것 — 콤보를 달성한 시각을 경로 위 점으로 옮길 때 쓴다.
// 경계점 시각은 구간이 들고 있다: 구간의 시작 경계점은 start_at, 끝 경계점은 end_at에 지났다
public final class RouteTimeline {

    private final UUID userId;
    private final List<RoutePoint> points;
    // 경계점 번호 순이라 시각도 오름차순이다 — 이진 탐색에 기댄다
    private final LocalDateTime[] times;

    private RouteTimeline(UUID userId, List<RoutePoint> points, LocalDateTime[] times) {
        this.userId = userId;
        this.points = points;
        this.times = times;
    }

    // 구간이 경로를 빈틈없이 덮지 못하면 점을 고를 수 없다 — 그 참가자는 기록이 없는 것처럼 다룬다
    public static Optional<RouteTimeline> of(RecordedRoute route) {
        // 조각을 이어 기록의 경계점 전체로 편다 — 콤보 점은 조각을 모르고 시각으로만 찾는다
        List<RoutePoint> points = new ArrayList<>();
        int[] offsets = new int[route.segmentPolylines().size()];
        int[] sizes = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            List<RoutePoint> segment = PolylineDecoder.decode(route.segmentPolylines().get(i));
            offsets[i] = points.size();
            sizes[i] = segment.size();
            points.addAll(segment);
        }
        if (points.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime[] times = new LocalDateTime[points.size()];
        for (RecordedSplitTime split : route.splits()) {
            int segment = split.segmentNumber() - 1;
            if (segment < 0 || segment >= offsets.length || split.routeEndIndex() >= sizes[segment]) {
                return Optional.empty();
            }
            int end = offsets[segment] + split.routeEndIndex();
            // 끊긴 곳에 걸친 구간([0, 0])의 시작 경계점은 앞 조각의 마지막 점이다 —
            // 여기를 비우면 1번 조각이 점 하나뿐일 때 그 점의 시각이 없어 참가자 전체가 빠진다
            int start = split.routeStartIndex() == split.routeEndIndex()
                    ? end - 1
                    : offsets[segment] + split.routeStartIndex();
            if (start < 0) {
                return Optional.empty();
            }
            times[start] = split.startAt();
            times[end] = split.endAt();
        }
        if (Arrays.stream(times).anyMatch(Objects::isNull)) {
            return Optional.empty();
        }
        return Optional.of(new RouteTimeline(route.userId(), points, times));
    }

    public UUID userId() {
        return userId;
    }

    // 그 시각에 가장 가까운 경계점을 그대로 돌려준다 — 새 좌표를 만들지 않아 그려진 경로에서 벗어나지 않는다.
    // 그 사이에 경계점이 없어도(아주 느리게 달렸다) 가장 가까운 점이 잡힌다. 똑같이 가까우면 앞의 점이다
    public ComboPoint nearest(LocalDateTime at) {
        int found = Arrays.binarySearch(times, at);
        if (found >= 0) {
            return pointAt(found);
        }
        int after = -found - 1;
        if (after == 0) {
            return pointAt(0);
        }
        if (after == times.length) {
            return pointAt(times.length - 1);
        }
        Duration toBefore = Duration.between(times[after - 1], at);
        Duration toAfter = Duration.between(at, times[after]);
        return pointAt(toAfter.compareTo(toBefore) < 0 ? after : after - 1);
    }

    private ComboPoint pointAt(int index) {
        RoutePoint point = points.get(index);
        return new ComboPoint(point.latitude(), point.longitude());
    }
}
