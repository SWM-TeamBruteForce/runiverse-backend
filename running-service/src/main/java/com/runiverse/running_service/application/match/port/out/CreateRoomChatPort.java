package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoomChat;

public interface CreateRoomChatPort {

    // 새로 보낸 채팅 한 건을 저장한다 — 생긴 ID와 보낸 시각(created_at)을 돌려준다
    SavedRoomChat create(RunningRoomChat chat);
}
