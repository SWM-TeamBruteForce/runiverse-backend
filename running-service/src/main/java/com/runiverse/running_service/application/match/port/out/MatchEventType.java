package com.runiverse.running_service.application.match.port.out;

// 방 상태 이벤트는 방 종류로 갈린다 — 매칭 방은 MATCH_*, 초대 방은 INVITE_ROOM_*만 받는다.
// 매칭 방: MATCH_STARTED는 확정된 그 순간 한 번, 나머지 갱신과 연결 직후 스냅샷은 MATCH_ROOM_UPDATED다.
// 초대 방: 연결 직후 스냅샷은 INVITE_ROOM_ENTRANCE(startsInMs를 더 싣는다), 그 밖은 INVITE_ROOM_UPDATED다 —
// 마감이 매칭 성사가 아니라 확정 연출을 두지 않는다.
// RUNNING_READY만 RoomInfo가 아닌 페이로드를 싣는다
public enum MatchEventType {
    MATCH_STARTED,
    MATCH_ROOM_UPDATED,
    INVITE_ROOM_ENTRANCE,
    INVITE_ROOM_UPDATED,
    RUNNING_READY
}
