package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

public interface AppendRunningTrackPort {

    int append(Long runningRoomId, UserId userId, List<TrackPoint> points);
}
