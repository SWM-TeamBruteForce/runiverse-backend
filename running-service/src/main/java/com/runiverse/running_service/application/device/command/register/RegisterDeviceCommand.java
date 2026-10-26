package com.runiverse.running_service.application.device.command.register;

import java.util.UUID;

public record RegisterDeviceCommand(
        UUID userId,
        String deviceId,
        String pushToken,
        String platform,
        String appVersion
) {

}
