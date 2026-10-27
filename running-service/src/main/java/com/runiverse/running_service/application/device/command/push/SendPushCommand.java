package com.runiverse.running_service.application.device.command.push;

import java.util.Map;
import java.util.UUID;

public record SendPushCommand(
        UUID userId,
        String title,
        String body,
        Map<String, String> data
) {

}
