package com.runiverse.running_service.application.app.command.stream;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;

import java.util.UUID;

public record CloseAppStreamCommand(UUID userId, AppStreamConnection connection) {

}
