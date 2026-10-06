package com.runiverse.running_service.application.running.common;

import java.time.LocalDateTime;

// 고정 거리 경계에 놓인 점. 위치·시각은 실측점 사이를 보간해 만든다.
// 이 목록이 곧 route_polyline이자 running_splits의 경계다.
public record BoundaryPoint(
        int distanceMeters,        // 0, 10, 20 … 시작점부터 이 지점까지의 거리
        double latitude,
        double longitude,
        LocalDateTime recordedAt,
        // 이 경계 직전(또는 같은 자리)의 실측점 인덱스.
        // 고도·케이던스는 보간점이 아니라 구간에 속한 실측점에서 뽑으므로 그 범위를 여기서 얻는다
        int sourceIndex
) {

}
