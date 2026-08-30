package com.runiverse.running_service.application.running.port.out;

// 러닝 중 누적 거리와 그 마지막 좌표.
// 마지막 좌표가 있어야 배치와 배치 사이 구간이 이어지고,
// 마지막 순번이 있어야 재연결 재전송분을 거리에서 건너뛴다
public record RunningDistance(
        double meters,
        long lastSequence,
        Double lastLatitude,
        Double lastLongitude
) {

    // 좌표를 한 번도 못 받은 상태 — lastSequence가 -1이라 순번 0도 새 좌표로 잡힌다
    public static RunningDistance empty() {
        return new RunningDistance(0, -1, null, null);
    }

    public boolean hasLastPoint() {
        return lastLatitude != null && lastLongitude != null;
    }

    // 전송 payload는 정수 m다 — 누적은 double로 들고 있다가 내보낼 때만 반올림한다
    public int metersRounded() {
        return (int) Math.round(meters);
    }
}
