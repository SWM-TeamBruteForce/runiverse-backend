package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.List;

public interface LoadMatchPlayersPort {

    // RunningRoom의 RoomSession은 playerId만 갖는다 — 화면에 그릴 값은 여기서 읽는다.
    // 현재 배정된 참가자만 반환한다(is_connected = true)
    List<MatchPlayer> loadPlayers(RunningRoomId runningRoomId);
}
