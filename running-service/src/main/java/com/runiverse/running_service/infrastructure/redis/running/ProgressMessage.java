package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.RunningProgress;

import java.util.UUID;

public record ProgressMessage(
        Long runningRoomId,
        UUID userId,
        int distanceMeters,
        Integer targetDistanceMeters,
        Integer currentPaceSecondsPerKm,
        boolean paused
) {

    public static ProgressMessage of(Long runningRoomId, RunningProgress progress) {
        return new ProgressMessage(
                runningRoomId,
                progress.userId(),
                progress.distanceMeters(),
                progress.targetDistanceMeters(),
                progress.currentPaceSecondsPerKm(),
                progress.paused());
    }

    public RunningProgress toProgress() {
        return new RunningProgress(
                userId, distanceMeters, targetDistanceMeters, currentPaceSecondsPerKm, paused);
    }
}
