package com.runiverse.running_service.application.match.command.apply;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyMatchCommand(
        UUID userId,
        LocalDateTime scheduledStartAt,
        int targetDistanceMeters
) {

}
