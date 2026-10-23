package com.runiverse.running_service.application.running.command.status;

import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;

import java.util.UUID;

// targetDistanceMeters는 진행 통지에 그대로 실린다 — 목표 없는 솔로 방이면 null
public record ChangeLiveRunningStatusCommand(UUID userId, Long runningRoomId,
                                             Integer targetDistanceMeters, LiveRunningStatus status) {

}
