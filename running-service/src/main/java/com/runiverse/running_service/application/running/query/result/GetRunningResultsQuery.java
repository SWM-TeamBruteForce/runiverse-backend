package com.runiverse.running_service.application.running.query.result;

import java.util.UUID;

public record GetRunningResultsQuery(Long runningRoomId, UUID viewerId) {

}
