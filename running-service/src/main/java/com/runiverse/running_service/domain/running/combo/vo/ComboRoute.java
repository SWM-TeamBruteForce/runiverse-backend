package com.runiverse.running_service.domain.running.combo.vo;

import com.runiverse.running_service.domain.running.combo.exception.ComboRouteRequiredException;

import java.util.List;

// 한 참가자의 콤보 달성 위치 목록 — i번째 점이 i콤보를 달성한 자리다.
// 기록 경로 위에 점을 찍는 용도라 순서가 곧 의미다
public record ComboRoute(List<ComboPoint> points) {

    public ComboRoute {
        // 기록이 없는 참가자는 ComboRoute 자체가 없다 — 빈 목록으로 그 상태를 흉내 내지 않는다
        if (points == null || points.isEmpty()) {
            throw new ComboRouteRequiredException();
        }
        points = List.copyOf(points);   // 방어 복사 + 불변
    }

    public int size() {
        return points.size();
    }
}
