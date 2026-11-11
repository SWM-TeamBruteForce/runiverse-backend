package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

// 저장된 채팅 한 행 — 조립에 필요한 값만 추린다. 애그리거트를 올리지 않는다.
// content는 지운 메시지여도 행에 남은 원문 그대로다 — API에 내보낼지는 조립기가 deletedAt으로 가른다
public record RoomChatRow(
        Long chatId,
        Long runningRoomId,
        UUID senderId,
        String content,
        // 고친 적 없으면 null
        LocalDateTime editedAt,
        // 지우지 않았으면 null
        LocalDateTime deletedAt,
        LocalDateTime createdAt
) {

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
