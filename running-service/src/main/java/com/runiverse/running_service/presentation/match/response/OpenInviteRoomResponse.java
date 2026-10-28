package com.runiverse.running_service.presentation.match.response;

// 방 정보는 응답이 아니라 연결 직후 스트림(INVITE_ROOM_ENTRANCE)이 나른다 — 두 벌로 내리지 않는다
public record OpenInviteRoomResponse(
        Long runningRoomId
) {

}
