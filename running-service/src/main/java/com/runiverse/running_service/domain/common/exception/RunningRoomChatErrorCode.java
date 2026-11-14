package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 도메인 예외는 500으로 응답한다 — 남의 채팅·지운 채팅(404)·시작된 방(409)은 application이 먼저 판정한다.
// 수정·삭제 코드는 그 판정을 빠뜨렸을 때 잘못된 변경을 막는 마지막 방어선이다
@Getter
@RequiredArgsConstructor
public enum RunningRoomChatErrorCode implements ErrorCode {
    // 식별자
    INVALID_RUNNING_ROOM_CHAT_ID("INVALID_RUNNING_ROOM_CHAT_ID", "채팅 ID가 올바르지 않습니다."),
    // 필수 값
    RUNNING_ROOM_CHAT_ROOM_REQUIRED("RUNNING_ROOM_CHAT_ROOM_REQUIRED", "채팅이 오간 방은 필수입니다."),
    RUNNING_ROOM_CHAT_SENDER_REQUIRED("RUNNING_ROOM_CHAT_SENDER_REQUIRED", "채팅을 보낸 사람은 필수입니다."),
    RUNNING_ROOM_CHAT_CONTENT_REQUIRED("RUNNING_ROOM_CHAT_CONTENT_REQUIRED", "채팅 내용은 필수입니다."),
    RUNNING_ROOM_CHAT_CONTENT_TOO_LONG("RUNNING_ROOM_CHAT_CONTENT_TOO_LONG", "채팅 내용은 200자 이하여야 합니다."),
    RUNNING_ROOM_CHAT_CHANGED_AT_REQUIRED("RUNNING_ROOM_CHAT_CHANGED_AT_REQUIRED", "채팅 수정·삭제 시각은 필수입니다."),
    // 수정·삭제
    RUNNING_ROOM_CHAT_NOT_SAVED("RUNNING_ROOM_CHAT_NOT_SAVED", "저장되지 않은 채팅은 고치거나 지울 수 없습니다."),
    NOT_RUNNING_ROOM_CHAT_SENDER("NOT_RUNNING_ROOM_CHAT_SENDER", "보낸 사람만 채팅을 고치거나 지울 수 있습니다."),
    RUNNING_ROOM_CHAT_DELETED("RUNNING_ROOM_CHAT_DELETED", "삭제된 채팅입니다.");
    private final String code;
    private final String message;
}
