package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.command.finish.TrackDistance;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.Comparator;
import java.util.List;

public final class RunningDistanceAccumulator {

    private RunningDistanceAccumulator() {
    }

    public static RunningDistance accumulate(RunningDistance from, List<TrackPoint> points) {
        double meters = from.meters();
        long lastSequence = from.lastSequence();
        Double lastLatitude = from.lastLatitude();
        Double lastLongitude = from.lastLongitude();
        // 배치 안이 뒤섞여 와도 이어붙인 거리가 맞으려면 순번 순서로 훑어야 한다
        List<TrackPoint> ordered = points.stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .toList();
        for (TrackPoint point : ordered) {
            // 이미 반영한 순번은 건너뛴다 — 재연결하면 클라가 순번 0부터 다시 보낸다(api-spec 5-D).
            // 뒤늦게 메워진 좌표도 여기서 빠져 거리가 그만큼 모자란다. 최종 기록이 바로잡는다
            if (point.sequence() <= lastSequence) {
                continue;
            }
            if (lastLatitude != null) {
                meters += TrackDistance.between(
                        lastLatitude, lastLongitude, point.latitude(), point.longitude());
            }
            lastSequence = point.sequence();
            lastLatitude = point.latitude();
            lastLongitude = point.longitude();
        }
        return new RunningDistance(meters, lastSequence, lastLatitude, lastLongitude);
    }
}
