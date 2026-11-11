package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoomChat;

import java.time.LocalDateTime;

// 고치거나 지우려고 잠가 읽은 메시지 한 건 — 바꾸기는 도메인이, 응답 모양은 조립기가 맡는다.
// 도메인 RunningRoomChat은 보낸 시각을 갖지 않아(SavedRoomChat과 같은 이유) 행의 created_at을 함께 싣는다
public record LockedRoomChat(
        RunningRoomChat chat,
        LocalDateTime createdAt
) {

}
