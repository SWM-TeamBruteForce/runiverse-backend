package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.out.RunningConnection;

import java.util.UUID;

// runningRoomId·targetDistanceMeters는 RUNNING_START 전에 끊긴 연결이면 null이다
public record RemoveRunningSessionCommand(UUID userId, RunningConnection connection,
                                          Long runningRoomId, Integer targetDistanceMeters) {

}
