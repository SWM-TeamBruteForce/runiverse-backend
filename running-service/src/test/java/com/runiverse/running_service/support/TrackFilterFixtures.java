package com.runiverse.running_service.support;

import com.runiverse.running_service.application.running.common.FilteredTrack;
import com.runiverse.running_service.application.running.common.TrackDistance;
import com.runiverse.running_service.application.running.common.TrackFilterProperties;
import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public final class TrackFilterFixtures {

    // 운영 시작값과 같다 — 정확도 50m, 최대 속도 12m/s, 정지 5~8m·10초,
    // 관측 안 된 칸 10초·500m·1.5m/s·×1.3·300초·출발 직후 7m/s
    public static final TrackFilterProperties DEFAULT_PROPERTIES = new TrackFilterProperties(
            50.0, 12.0, 5.0, 8.0, 10, 10, 500.0, 1.5, 1.3, 300, 7.0);

    private TrackFilterFixtures() {
    }

    // 필터를 거치지 않은 트랙 — 모든 칸을 인정한 것으로 본다.
    // 경계점·구간 조립처럼 필터 뒤 단계만 따로 볼 때 쓴다. 집계는 쓰지 않으므로 비워 둔다
    public static FilteredTrack unfiltered(List<TrackPoint> points) {
        double[] seconds = new double[points.size()];
        if (!points.isEmpty()) {
            LocalDateTime origin = points.get(0).recordedAt();
            for (int i = 0; i < points.size(); i++) {
                seconds[i] = Duration.between(origin, points.get(i).recordedAt()).toMillis() / 1_000.0;
            }
        }
        return new FilteredTrack(points, TrackDistance.cumulativeMeters(points), seconds, null);
    }
}
