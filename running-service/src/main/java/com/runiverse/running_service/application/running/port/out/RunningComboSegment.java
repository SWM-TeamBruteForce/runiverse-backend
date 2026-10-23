package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Instant;

// 붙었다 끊긴 콤보 한 구간 — 방이 닫힐 때 running_combos 한 행이 된다.
// 구간 번호는 담지 않는다: 시작 시각 순서가 곧 번호라 닫을 때 매긴다.
// first·second는 관계(RunningComboPair)에서 정렬된 그대로 받는다
public record RunningComboSegment(
        UserId first,
        UserId second,
        Instant startAt,
        // 쌓인 시각 — 끊긴 시각이 아니다
        Instant endAt,
        int comboCount
) {

}
