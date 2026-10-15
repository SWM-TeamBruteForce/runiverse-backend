package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

// 러닝 중 누적 거리와 그 마지막 좌표.
// 마지막 좌표가 있어야 배치와 배치 사이 구간이 이어지고,
// 마지막 순번이 있어야 재연결 재전송분을 거리에서 건너뛴다
public record RunningDistance(
        double meters,
        long lastSequence,
        Double lastLatitude,
        Double lastLongitude,
        // 마지막으로 반영한 좌표의 페이스. 발행만 하고 흘려보내면
        // 재연결한 클라의 RUNNING_STARTED 스냅샷이 남의 페이스를 복구할 길이 없다.
        // 단말이 못 재면 null이고, null인 채로 저장해야 스냅샷도 null을 그대로 싣는다
        Integer lastPaceSecondsPerKm,
        // 의심점 — 참가자가 머물고 있다고 의심하는 기준 좌표. 반경 밖 좌표가 오면 옮겨진다.
        // 콤보가 서 있는 사람과 달리는 사람을 가르는 근거다. 관측한 좌표가 없으면 null이다
        Double anchorLatitude,
        Double anchorLongitude,
        // 마지막으로 반영한 좌표의 측정 시각 — 배치 사이 칸이 일시정지 공백인지 가른다. 옛 값이면 null
        LocalDateTime lastRecordedAt
) {

    // 좌표를 한 번도 못 받은 상태 — lastSequence가 -1이라 순번 0도 새 좌표로 잡힌다
    public static RunningDistance empty() {
        return new RunningDistance(0, -1, null, null, null, null, null, null);
    }

    public boolean hasLastPoint() {
        return lastLatitude != null && lastLongitude != null;
    }

    // 전송 payload는 정수 m다 — 누적은 double로 들고 있다가 내보낼 때만 반올림한다
    public int metersRounded() {
        return (int) Math.round(meters);
    }
}
