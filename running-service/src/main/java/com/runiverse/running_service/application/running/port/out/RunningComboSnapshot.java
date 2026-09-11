package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Instant;

// 판정 시점에 본 참가자 한 명 — 마지막으로 받은 누적 거리와 그 기준 시각, 그 구간의 속도다.
// 기준 시각이 있어야 오래 조용한 참가자를 비교에서 뺄 수 있고,
// 속도가 있어야 배치 도착이 어긋난 참가자들의 누적 거리를 같은 시각으로 밀어 맞출 수 있다
public record RunningComboSnapshot(
        UserId userId,
        double meters,
        Instant recordedAt,
        // 마지막 두 배치 사이의 평균 속도(m/s) — 첫 배치는 이전 값이 없어 0이다
        double speedMetersPerSecond
) {

}
