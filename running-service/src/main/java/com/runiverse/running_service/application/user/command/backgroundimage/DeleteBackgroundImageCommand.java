package com.runiverse.running_service.application.user.command.backgroundimage;

import java.util.UUID;

public record DeleteBackgroundImageCommand(
        UUID userId
) {

}
