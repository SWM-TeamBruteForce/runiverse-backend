package com.runiverse.running_service.application.device.port.in;

import com.runiverse.running_service.application.device.command.push.SendPushCommand;

public interface SendPushUsecase {

    void handle(SendPushCommand command);
}
