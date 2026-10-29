package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.out.PushMessage;

import java.util.UUID;

public record SendPushCommand(UUID userId, PushMessage message) {

}
