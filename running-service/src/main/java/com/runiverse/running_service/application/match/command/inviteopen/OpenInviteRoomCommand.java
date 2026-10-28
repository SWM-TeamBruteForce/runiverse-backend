package com.runiverse.running_service.application.match.command.inviteopen;

import java.time.LocalDateTime;
import java.util.UUID;

public record OpenInviteRoomCommand(UUID userId, LocalDateTime scheduledStartAt, int targetDistanceMeters) {

}
