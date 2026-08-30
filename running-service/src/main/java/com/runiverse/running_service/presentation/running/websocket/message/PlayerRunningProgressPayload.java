package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.running.port.out.RunningProgress;

public record PlayerRunningProgressPayload(
        String userId,
        int distanceMeters,
        Integer targetDistanceMeters,
        Integer currentPaceSecondsPerKm,
        boolean paused
) {

    public static PlayerRunningProgressPayload from(RunningProgress progress) {
        return new PlayerRunningProgressPayload(
                progress.userId().toString(),
                progress.distanceMeters(),
                progress.targetDistanceMeters(),
                progress.currentPaceSecondsPerKm(),
                progress.paused());
    }
}
