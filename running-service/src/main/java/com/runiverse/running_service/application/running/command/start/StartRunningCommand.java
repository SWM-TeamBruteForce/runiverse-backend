package com.runiverse.running_service.application.running.command.start;

import java.util.UUID;

public record StartRunningCommand(UUID userId, Long runningRoomId) {

}
