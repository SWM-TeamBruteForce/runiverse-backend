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
        int routeStartIndex,
        int routeEndIndex
) {

}
