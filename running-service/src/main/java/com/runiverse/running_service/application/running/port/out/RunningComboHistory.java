package com.runiverse.running_service.application.running.port.out;

import java.util.List;

// 방이 닫힐 때 running_combos로 옮길 재료 — 끊겨서 닫힌 구간과 그때까지 이어지던 관계다.
// 이어지던 관계를 구간으로 닫는 것(유지 횟수 계산)은 1회 길이를 아는 application이 한다
public record RunningComboHistory(
        List<RunningComboSegment> closedSegments,
        List<RunningComboPair> openPairs
) {

}
