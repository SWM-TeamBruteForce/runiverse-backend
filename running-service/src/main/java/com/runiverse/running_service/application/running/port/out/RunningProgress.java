package com.runiverse.running_service.application.running.port.out;

import java.util.UUID;

public record RunningProgress(
        UUID userId,
        int distanceMeters,
        // 목표 없는 솔로 방은 target_distance가 null이다(erd.md)
        Integer targetDistanceMeters,
        // 마지막 좌표의 값을 그대로 옮긴다 — 단말이 못 재면 null
        Integer currentPaceSecondsPerKm,
        boolean paused
) {

}
