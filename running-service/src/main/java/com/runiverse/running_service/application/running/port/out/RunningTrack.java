package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public record RunningTrack(String raw, List<TrackPoint> points) {

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
