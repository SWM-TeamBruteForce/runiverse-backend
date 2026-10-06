package com.runiverse.running_service.application.match.port.out;

// 방 ID를 최상위로 올린다 — 채널 키는 페이로드 종류와 무관하게 언제나 필요하다.
// room·ready는 타입에 따라 하나만 채워진다
public record MatchStreamEvent(MatchEventType type, Long runningRoomId,
                               RoomInfo room, RunningReady ready) {

    // 갱신·스냅샷은 전부 이쪽이다 — 확정된 그 순간만 started()를 쓴다
    public static MatchStreamEvent updated(RoomInfo room) {
        return new MatchStreamEvent(MatchEventType.MATCH_ROOM_UPDATED,
                room.runningRoomId(), room, null);
    }

    public static MatchStreamEvent started(RoomInfo room) {
        return new MatchStreamEvent(MatchEventType.MATCH_STARTED,
                room.runningRoomId(), room, null);
    }

    public static MatchStreamEvent runningReady(RunningReady ready) {
        return new MatchStreamEvent(MatchEventType.RUNNING_READY,
                ready.runningRoomId(), null, ready);
    }
}
