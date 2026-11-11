package com.runiverse.running_service.application.match.port.out;

public interface LoadRoomChatsPort {

    // 방의 채팅을 최신순(ID 역순)으로 limit개까지 읽는다 — beforeId가 있으면 그보다 오래된 것부터.
    // 지운 메시지도 빼지 않는다(자리를 지킨다). 내용을 숨기는 건 조립기의 몫이다
    RoomChatPage loadPage(Long runningRoomId, Long beforeId, int limit);
}
