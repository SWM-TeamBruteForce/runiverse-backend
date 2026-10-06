package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadMatchRoomPort {

    // running_players엔 방 ID가 없다 — 현재 배정된 방은 running_room_sessions의
    // is_connected=true인 행이 갖는다. 세션의 키가 유저라 신청을 거치지 않고 바로 찾는다
    Optional<RunningRoomId> findAssignedRoom(UserId userId);
}
