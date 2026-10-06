package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.out.RunningConnection;

import java.util.UUID;

public record RemoveRunningSessionCommand(UUID userId, RunningConnection connection) {

}
