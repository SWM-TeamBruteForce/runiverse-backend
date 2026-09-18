package com.runiverse.running_service.application.match.port.out;

// 후보 순위를 매기는 데 필요한 값만 담는다 — 방을 통째로 읽으면 쓰지도 않을 세션까지 딸려온다
public record MatchCandidate(
        Long runningRoomId,
        int avgPaceSecondsPerKm,
        // 신청자가 이 방을 나간 횟수 — 방 전체 합이 아니다.
        // 거쳐 간 적 없는 방은 세션 행이 없어 0이다
        int myLeaveCount
) {

}
