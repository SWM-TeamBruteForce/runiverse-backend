package com.runiverse.running_service.application.running.query.snapshot;

import java.util.UUID;

public record GetRunningSnapshotQuery(UUID userId, Long runningRoomId) {

}
