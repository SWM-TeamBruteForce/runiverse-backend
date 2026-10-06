package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.combo.StartRunningComboCommand;

public interface StartRunningComboUsecase {

    void handle(StartRunningComboCommand command);
}
