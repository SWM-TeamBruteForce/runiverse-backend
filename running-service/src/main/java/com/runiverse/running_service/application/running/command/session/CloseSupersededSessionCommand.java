package com.runiverse.running_service.application.running.command.session;

import java.util.UUID;

public record CloseSupersededSessionCommand(UUID userId, String winnerSessionId) {

}
