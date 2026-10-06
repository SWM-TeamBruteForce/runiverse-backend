package com.runiverse.running_service.application.running.port.out;

import java.util.UUID;

public record RunningComboRelation(
        UUID first,
        UUID second,
        // second가 first보다 앞서 있으면 양수 — 받는 쪽이 second면 부호를 뒤집어 쓴다.
        // 좌표상 거리가 아니라 누적 주행 거리의 차이다
        int gapMeters,
        int comboCount,
        int maxComboCount
) {

}
