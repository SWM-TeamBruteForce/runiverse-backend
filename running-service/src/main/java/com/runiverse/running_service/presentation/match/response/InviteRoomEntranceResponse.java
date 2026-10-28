package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;

// INVITE_ROOM_ENTRANCE의 data — RoomInfoResponse와 같은 필드에 startsInMs를 같은 깊이로 더한다.
// 감싸지 않고 펼친다 — 클라가 다른 방 상태 이벤트와 같은 파서로 읽고 남은 시간만 더 꺼낸다
public record InviteRoomEntranceResponse(
        Long runningRoomId,
        RunningRoomType type,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        Integer teamAveragePaceSecondsPerKm,
        List<RoomInfoResponse.PlayerResponse> players,
        List<RoomInfoResponse.InvitationResponse> invitations,
        // 이 이벤트를 보낸 시점 기준 시작까지 남은 밀리초 — 이미 지났으면 0
        long startsInMs
) {

    public static InviteRoomEntranceResponse from(MatchStreamEvent event) {
        RoomInfoResponse room = RoomInfoResponse.from(event.room());
        return new InviteRoomEntranceResponse(
                room.runningRoomId(),
                room.type(),
                room.status(),
                room.scheduledStartAt(),
                room.closeAt(),
                room.targetDistanceMeters(),
                room.teamAveragePaceSecondsPerKm(),
                room.players(),
                room.invitations(),
                event.startsInMs());
    }
}
