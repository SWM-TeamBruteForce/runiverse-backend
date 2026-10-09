package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.List;

// 필터를 거친 트랙. 두 누적 배열은 점마다 그 점까지 인정한 거리·시간이다 —
// 거부한 칸에서는 둘이 함께 멈추고, 경계점 보간이 이 두 배열 위에서 끝난다
public record FilteredTrack(
        List<TrackPoint> points,              // 정확도·튐으로 뺀 점을 제외한 계산 대상
        double[] cumulativeMeters,
        double[] cumulativeMovingSeconds,
        TrackFilterSummary summary

) {

    public double totalMeters() {
        return cumulativeMeters.length == 0 ? 0 : cumulativeMeters[cumulativeMeters.length - 1];
    }
}
