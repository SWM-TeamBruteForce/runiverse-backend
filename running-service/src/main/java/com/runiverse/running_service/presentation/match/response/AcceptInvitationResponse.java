package com.runiverse.running_service.presentation.match.response;

// 방 정보는 응답이 아니라 스트림을 연 직후의 INVITE_ROOM_ENTRANCE가 나른다 — 두 벌로 내리지 않는다
public record AcceptInvitationResponse(
        Long runningRoomId
) {

}
