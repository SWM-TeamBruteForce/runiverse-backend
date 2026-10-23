package com.runiverse.running_service.application.running.common;

// 방 하나의 콤보를 옮긴 결과 — 로그에 남겨 러닝 중 쌓인 것과 DB에 남은 것을 비교한다
public record RunningComboArchive(
        // 러닝 중 끊겨 Redis 구간 해시에 쌓여 있던 구간 수
        int closedCount,
        // 방이 닫힐 때까지 이어지던 관계를 닫아 만든 구간 수
        int openCount,
        // 실제로 넣은 행 수
        int rowCount
) {

    public static RunningComboArchive empty() {
        return new RunningComboArchive(0, 0, 0);
    }
}
