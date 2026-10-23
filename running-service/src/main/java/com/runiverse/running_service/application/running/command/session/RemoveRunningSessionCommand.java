package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.out.RunningConnection;

import java.util.UUID;

// runningRoomId는 RUNNING_START 전에 끊긴 연결이면 null이다. targetDistanceMeters는 그때와 목표 없는 솔로 방에서 null이다
public record RemoveRunningSessionCommand(UUID userId, RunningConnection connection,
                                          Long runningRoomId, Integer targetDistanceMeters) {

}
