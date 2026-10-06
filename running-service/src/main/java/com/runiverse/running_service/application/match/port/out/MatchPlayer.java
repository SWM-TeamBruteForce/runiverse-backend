package com.runiverse.running_service.application.match.port.out;

import java.util.UUID;

public record MatchPlayer(
        UUID userId,
        int avgPaceSecondsPerKm
) {

}
