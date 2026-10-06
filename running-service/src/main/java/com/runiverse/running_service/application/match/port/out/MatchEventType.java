package com.runiverse.running_service.application.match.port.out;

// MATCH_STARTED는 확정된 그 순간 한 번, 나머지 갱신과 연결 직후 스냅샷은 MATCH_ROOM_UPDATED다.
// RUNNING_READY만 RoomInfo가 아닌 페이로드를 싣는다
public enum MatchEventType {
    MATCH_STARTED,
    MATCH_ROOM_UPDATED,
    RUNNING_READY
}
