package com.runiverse.running_service.application.running.command.combo;

import java.util.UUID;

public record StartRunningComboCommand(Long runningRoomId, UUID userId) {

}
