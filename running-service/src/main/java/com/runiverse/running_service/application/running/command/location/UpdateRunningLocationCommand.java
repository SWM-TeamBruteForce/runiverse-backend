package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.List;
import java.util.UUID;

public record UpdateRunningLocationCommand(UUID userId, Long runningRoomId, Integer targetDistanceMeters,
                                           List<TrackPoint> points) {

}
