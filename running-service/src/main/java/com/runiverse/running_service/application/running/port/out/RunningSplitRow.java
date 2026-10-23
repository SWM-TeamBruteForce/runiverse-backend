package com.runiverse.running_service.application.running.port.out;

import java.util.UUID;

public record RunningSplitRow(
        UUID userId,
        int splitNumber,
        int distanceMeters,
        int durationSeconds,
        int averagePaceSecondsPerKm,
        Integer averageCadenceSpm,
        Integer elevationChangeMeters,
        int caloriesKcal,
        // 끝 경계점이 있는 조각 — 경로 범위는 이 조각 안의 점 번호다
        int segmentNumber,
        int routeStartIndex,
        int routeEndIndex
) {

}
