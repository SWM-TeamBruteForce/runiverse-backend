package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;

import java.util.UUID;

public record RunningResultPlayer(
        UUID userId,
        RunningPlayerStatus status,
        Integer totalDistanceMeters,
        Integer totalDurationSeconds,
        Integer totalCaloriesKcal,
        Integer averagePaceSecondsPerKm,
        Integer averageCadenceSpm,
        Integer totalElevationGainMeters
) {

}
