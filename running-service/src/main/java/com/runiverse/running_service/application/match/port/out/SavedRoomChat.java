package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDateTime;

// 저장하면서 생기는 값 두 개 — ID는 DB 시퀀스가, 보낸 시각은 created_at이 정본이다.
// 도메인 RunningRoomChat은 보낸 시각을 갖지 않아서, 응답·이벤트에 실을 값을 저장 결과로 돌려받는다
public record SavedRoomChat(
        Long chatId,
        LocalDateTime createdAt
) {

}
