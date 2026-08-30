package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.progress.BroadcastRunningProgressCommand;

public interface BroadcastRunningProgressUsecase {

    void handle(BroadcastRunningProgressCommand command);
}
