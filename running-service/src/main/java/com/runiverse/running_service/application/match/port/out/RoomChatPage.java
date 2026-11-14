package com.runiverse.running_service.application.match.port.out;

import java.util.List;

// 채팅 한 페이지 — 최신순(ID 역순)이다.
// nextBeforeId가 null이면 방의 첫 메시지까지 다 읽었다
public record RoomChatPage(
        List<RoomChatRow> rows,
        Long nextBeforeId
) {

}
