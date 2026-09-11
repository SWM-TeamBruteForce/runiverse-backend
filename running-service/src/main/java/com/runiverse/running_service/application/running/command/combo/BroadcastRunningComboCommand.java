package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;

public record BroadcastRunningComboCommand(Long runningRoomId, RunningComboUpdate update) {

}
