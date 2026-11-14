package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoomChat;

public interface UpdateRoomChatPort {

    // 고치거나 지운 메시지의 바뀐 값(content·edited_at·deleted_at)을 쓴다 — 같은 트랜잭션에서 loadForUpdate로 잠근 행이다
    void update(RunningRoomChat chat);
}
