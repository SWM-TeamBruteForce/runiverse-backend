package com.runiverse.running_service.application.match.port.out;

// 방 상태 이벤트는 방 종류로 갈린다 — 매칭 방은 MATCH_*, 초대 방은 INVITE_ROOM_*만 받는다.
// 연결 직후 스냅샷은 *_ENTRANCE, 그 밖의 갱신은 *_UPDATED다 — 이름만 보고 입장과 갱신을 가른다.
// 매칭 방: MATCH_CONFIRMED는 확정된 그 순간 한 번이다.
// 초대 방: 입장 이벤트가 startsInMs를 더 싣는다. 마감이 매칭 성사가 아니라 확정 연출을 두지 않는다.
// RUNNING_READY만 RoomInfo가 아닌 페이로드를 싣는다.
// ROOM_CHAT_*는 두 방에 똑같이 가서 어느 쪽 접두어도 붙이지 않는다 — 방 상태가 아니라 변경분이고, 보낸 사람에게는 가지 않는다
public enum MatchEventType {
    MATCH_CONFIRMED,
    MATCH_ROOM_ENTRANCE,
    MATCH_ROOM_UPDATED,
    INVITE_ROOM_ENTRANCE,
    INVITE_ROOM_UPDATED,
    RUNNING_READY,
    ROOM_CHAT_SENT
}
