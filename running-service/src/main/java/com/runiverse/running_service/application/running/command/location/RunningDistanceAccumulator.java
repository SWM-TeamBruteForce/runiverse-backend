package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.common.TrackDistance;
import com.runiverse.running_service.application.running.common.TrackFilterProperties;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.Comparator;
import java.util.List;

public final class RunningDistanceAccumulator {

    private RunningDistanceAccumulator() {
    }

    // 거리와 의심점을 같은 좌표로 한 번에 훑는다 — 의심점을 따로 저장하면 배치마다 왕복이 하나 는다.
    // 의심점 반경과 관측 정확도 상한은 트랙 필터의 정지 판정과 같다:
    // 기록과 콤보에서 "멈춤"의 뜻이 같아야 한다
    public static RunningDistanceAccumulation accumulate(
            RunningDistance from, List<TrackPoint> points, TrackFilterProperties filter) {
        double meters = from.meters();
        long lastSequence = from.lastSequence();
        int lastPauseSegment = from.lastPauseSegment();
        Double lastLatitude = from.lastLatitude();
        Double lastLongitude = from.lastLongitude();
        Integer lastPace = from.lastPaceSecondsPerKm();
        Double anchorLatitude = from.anchorLatitude();
        Double anchorLongitude = from.anchorLongitude();
        boolean observed = false;
        boolean moved = false;
        boolean pauseCrossed = false;
        // 배치 안이 뒤섞여 와도 이어붙인 거리가 맞으려면 순번 순서로 훑어야 한다
        List<TrackPoint> ordered = points.stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .toList();
        for (TrackPoint point : ordered) {
            // 이미 반영한 순번은 건너뛴다 — 재연결하면 클라가 순번 0부터 다시 보낸다.
            // 뒤늦게 메워진 좌표도 여기서 빠져 거리가 그만큼 모자란다. 최종 기록이 바로잡는다
            if (point.sequence() <= lastSequence) {
                continue;
            }
            // 일시정지 구간 번호가 바뀐 칸은 일시정지 동안 관측하지 않은 이동이다 — 누적에서도 뺀다.
            // 빼지 않으면 일시정지 중 이동으로 목표에 닿거나 앞선 참가자와 콤보가 붙는다
            boolean crossesPause = lastLatitude != null && point.pauseSegment() != lastPauseSegment;
            if (lastLatitude != null && !crossesPause) {
                meters += TrackDistance.between(
                        lastLatitude, lastLongitude, point.latitude(), point.longitude());
            }
            // 경계를 넘으면 의심점을 비운다 — 경계 뒤 첫 관측이 첫 의심점이 되어 이동으로 보지 않는다.
            // 정확도 판정보다 먼저다: 경계 뒤 첫 좌표가 관측이 아니어도 비워 둔 채로 저장돼 다음 배치로 이어진다
            if (crossesPause) {
                pauseCrossed = true;
                anchorLatitude = null;
                anchorLongitude = null;
            }
            lastSequence = point.sequence();
            lastPauseSegment = point.pauseSegment();
            lastLatitude = point.latitude();
            lastLongitude = point.longitude();
            // 거리에 반영한 좌표의 페이스만 남긴다 — 건너뛴 재전송분의 페이스를
            // 최신값으로 삼으면 화면이 지나간 구간의 속도로 되돌아간다
            lastPace = point.currentPaceSecondsPerKm();
            // 정확도가 나쁜 좌표는 관측이 아니다 — 터널에서 튄 좌표로 의심점이 옮겨지면
            // 서 있는 사람이 달리는 것으로 보인다. 거리에는 반영한다
            if (point.accuracyMeters() > filter.maxAccuracyMeters()) {
                continue;
            }
            observed = true;
            if (anchorLatitude == null) {
                // 첫 의심점 — 옮겨진 것이 아니다. 출발선에 선 참가자는 여기서 서 있음으로 시작한다
                anchorLatitude = point.latitude();
                anchorLongitude = point.longitude();
                continue;
            }
            if (TrackDistance.between(anchorLatitude, anchorLongitude, point.latitude(), point.longitude())
                    > radius(point, filter)) {
                anchorLatitude = point.latitude();
                anchorLongitude = point.longitude();
                moved = true;
            }
        }
        return new RunningDistanceAccumulation(
                new RunningDistance(meters, lastSequence, lastPauseSegment, lastLatitude, lastLongitude,
                        lastPace, anchorLatitude, anchorLongitude),
                observed,
                moved,
                pauseCrossed);
    }

    // 트랙 필터의 정지 반경과 같다 — 상한이 없으면 정확도가 나쁜 곳에서 달리는 사람도 반경 안에 머문 것으로 잡힌다
    private static double radius(TrackPoint point, TrackFilterProperties filter) {
        return Math.clamp(point.accuracyMeters(),
                filter.stopMinRadiusMeters(), filter.stopMaxRadiusMeters());
    }
}
