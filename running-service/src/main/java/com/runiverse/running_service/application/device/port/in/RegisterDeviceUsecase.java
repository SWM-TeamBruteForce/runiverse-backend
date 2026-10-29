package com.runiverse.running_service.application.device.port.in;

import com.runiverse.running_service.application.device.command.register.RegisterDeviceCommand;

public interface RegisterDeviceUsecase {

    void handle(RegisterDeviceCommand command);
}
