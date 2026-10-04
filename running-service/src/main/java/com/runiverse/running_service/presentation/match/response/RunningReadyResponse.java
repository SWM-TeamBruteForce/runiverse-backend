package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.port.out.RunningReady;

import java.time.LocalDateTime;

// SSE RUNNING_READY의 data — 클라가 발사 타이머를 걸 값만 담는다
public record RunningReadyResponse(Long runningRoomId,
                                   LocalDateTime scheduledStartAt,
                                   long startsInMs) {

    public static RunningReadyResponse from(RunningReady ready) {
        return new RunningReadyResponse(
                ready.runningRoomId(), ready.scheduledStartAt(), ready.startsInMs());
    }
}
