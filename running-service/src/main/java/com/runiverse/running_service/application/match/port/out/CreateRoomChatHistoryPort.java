package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;

public interface CreateRoomChatHistoryPort {

    // 고치기 직전 내용을 한 행 남긴다 — 메시지를 잠근 같은 트랜잭션에서 update보다 먼저 부른다.
    // 운영(신고 대응) 전용이라 쓰기만 하고 되읽지 않는다
    void create(RunningRoomChatHistory history);
}
