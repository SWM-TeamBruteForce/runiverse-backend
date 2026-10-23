package com.runiverse.running_service.application.running.common;

import java.time.LocalDateTime;

// 고정 거리 경계에 놓인 점. 위치·시각은 실측점 사이를 보간해 만든다.
// 이 목록이 곧 route_polyline이자 running_splits의 경계다.
public record BoundaryPoint(
        int distanceMeters,        // 0, 10, 20 … 시작점부터 이 지점까지의 거리
        double latitude,
        double longitude,
        LocalDateTime recordedAt, // 실제 시각 — 구간의 시작·끝 시각이 된다
        // 출발부터 이 지점까지 인정한 시간(초). 정지·거부한 칸은 빠져 있어
        // 두 경계의 차가 곧 그 구간을 움직인 시간이다
        double movingSeconds,
        // 이 경계 직전(또는 같은 자리)의 실측점 인덱스.
        // 고도·케이던스는 보간점이 아니라 구간에 속한 실측점에서 뽑으므로 그 범위를 여기서 얻는다
        int sourceIndex
) {

}
