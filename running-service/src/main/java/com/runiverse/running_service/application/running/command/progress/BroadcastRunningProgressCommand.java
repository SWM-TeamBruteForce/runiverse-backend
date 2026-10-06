package com.runiverse.running_service.application.running.command.progress;

import com.runiverse.running_service.application.running.port.out.RunningProgress;

public record BroadcastRunningProgressCommand(Long runningRoomId, RunningProgress progress) {

}
