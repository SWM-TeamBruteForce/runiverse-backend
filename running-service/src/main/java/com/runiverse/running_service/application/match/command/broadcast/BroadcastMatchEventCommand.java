package com.runiverse.running_service.application.match.command.broadcast;

import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;

public record BroadcastMatchEventCommand(MatchStreamEvent event) {

}
