package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.combo.BroadcastRunningComboCommand;

public interface BroadcastRunningComboUsecase {

    void handle(BroadcastRunningComboCommand command);
}
