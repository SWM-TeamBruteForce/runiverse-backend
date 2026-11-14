package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 대기방 채팅 — 방이 없거나 솔로이거나 배정이 없으면 ResourceErrorCode.NOT_FOUND를 쓴다.
// 남의 방이 있는지 없는지를 알려 주지 않으려고 404는 가르지 않는다
@Getter
@RequiredArgsConstructor
public enum RoomChatErrorCode implements ErrorCode {
    // 배정은 남아 있지만 러닝이 시작됐거나 방이 닫혔다(STARTED·FINISHED·CANCELLED) — 클라는 채팅 입력을 닫는다
    ROOM_CHAT_CLOSED("ROOM_CHAT_CLOSED", "지금은 채팅할 수 없습니다."),
    // 채팅 목록 커서가 깨졌다 — 서버가 준 값을 그대로 돌려보내지 않았다. 클라에는 요청 검증 실패와 같은 코드로 보인다
    INVALID_ROOM_CHAT_CURSOR("INVALID_REQUEST", "입력값이 올바르지 않습니다.");
    private final String code;
    private final String message;
}
