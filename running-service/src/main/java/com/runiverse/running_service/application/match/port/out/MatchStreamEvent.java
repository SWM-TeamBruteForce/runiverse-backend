package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.Duration;
import java.time.LocalDateTime;

// 방 ID를 최상위로 올린다 — 채널 키는 페이로드 종류와 무관하게 언제나 필요하다.
// room·ready는 타입에 따라 하나만 채워진다. startsInMs는 INVITE_ROOM_ENTRANCE만 싣는다 —
// 연결한 본인에게 바로 보내는 스냅샷이라 Redis로 나르지 않는다
public record MatchStreamEvent(MatchEventType type, Long runningRoomId,
                               RoomInfo room, RunningReady ready, Long startsInMs) {

    // 갱신은 전부 이쪽이다 — 방 종류로 이벤트 이름이 갈린다.
    // 매칭 방은 확정된 그 순간만 started()를 쓰고, 초대 방은 마감도 이 갱신으로 알린다
    public static MatchStreamEvent updated(RoomInfo room) {
        MatchEventType type = room.type() == RunningRoomType.INVITE
                ? MatchEventType.INVITE_ROOM_UPDATED
                : MatchEventType.MATCH_ROOM_UPDATED;
        return new MatchStreamEvent(type, room.runningRoomId(), room, null, null);
    }

    // 매칭 방 전용 — 초대 방의 마감은 매칭 성사가 아니라 확정 연출을 두지 않는다
    public static MatchStreamEvent started(RoomInfo room) {
        return new MatchStreamEvent(MatchEventType.MATCH_STARTED,
                room.runningRoomId(), room, null, null);
    }

    // 연결 직후 스냅샷 — 매칭 방은 갱신과 같은 이벤트이고, 초대 방은 입장 이벤트에 남은 시간을 더 싣는다.
    // now를 받는 건 테스트가 시각을 고정하기 위해서다
    public static MatchStreamEvent snapshot(RoomInfo room, LocalDateTime now) {
        if (room.type() != RunningRoomType.INVITE) {
            return updated(room);
        }
        // 이미 지났으면 0 — 음수를 내려보내지 않는다
        long startsInMs = Math.max(0L, Duration.between(now, room.scheduledStartAt()).toMillis());
        return new MatchStreamEvent(MatchEventType.INVITE_ROOM_ENTRANCE,
                room.runningRoomId(), room, null, startsInMs);
    }

    public static MatchStreamEvent runningReady(RunningReady ready) {
        return new MatchStreamEvent(MatchEventType.RUNNING_READY,
                ready.runningRoomId(), null, ready, null);
    }
}
