package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.RunningReady;

import java.util.UUID;

// 받을 유저는 싣지 않는다 — 받는 인스턴스가 자기 명부에서 꺼낸다.
// 다만 받지 않을 유저(excludedUserId)는 싣는다 — 보낸 사람이 어느 인스턴스에 붙어 있는지 발행하는 쪽은 모른다.
// room·ready·chat은 타입에 따라 하나만 채워져 나머지는 null로 직렬화된다
public record MatchEventMessage(MatchEventType type, Long runningRoomId,
                                RoomInfo room, RunningReady ready,
                                RoomChat chat, UUID excludedUserId) {

}
