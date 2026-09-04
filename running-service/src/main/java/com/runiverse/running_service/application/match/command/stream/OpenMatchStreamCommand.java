package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;

import java.util.UUID;

public record OpenMatchStreamCommand(UUID userId, MatchStreamConnection connection) {

}
