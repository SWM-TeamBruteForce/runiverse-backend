package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;

public interface BroadcastMatchEventUsecase {

    void handle(BroadcastMatchEventCommand command);
}
