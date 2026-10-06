package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.RunningReady;

// 대상 유저는 싣지 않는다 — 받는 인스턴스가 자기 명부에서 꺼낸다.
// room·ready는 타입에 따라 하나만 채워져 나머지는 null로 직렬화된다
public record MatchEventMessage(MatchEventType type, Long runningRoomId,
                                RoomInfo room, RunningReady ready) {

}
