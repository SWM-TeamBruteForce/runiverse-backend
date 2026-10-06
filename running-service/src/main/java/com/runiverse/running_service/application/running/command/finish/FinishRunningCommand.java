package com.runiverse.running_service.application.running.command.finish;

import java.util.UUID;

public record FinishRunningCommand(Long runningRoomId, UUID userId, boolean forced) {

}
