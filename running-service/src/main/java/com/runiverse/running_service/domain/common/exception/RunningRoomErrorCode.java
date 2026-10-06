package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningRoomErrorCode implements ErrorCode {
    INVALID_RUNNING_ROOM_ID("INVALID_RUNNING_ROOM_ID", "러닝방 ID가 올바르지 않습니다."),
    ROOM_TYPE_REQUIRED("ROOM_TYPE_REQUIRED", "러닝방 종류는 필수입니다."),
    UNSUPPORTED_ROOM_TYPE("UNSUPPORTED_ROOM_TYPE", "지원하지 않는 러닝방 종류입니다."),
    INVALID_ROOM_STATUS_TRANSITION("INVALID_ROOM_STATUS_TRANSITION", "허용되지 않는 러닝방 상태 변경입니다."),
    INVALID_PLAYER_COUNT("INVALID_PLAYER_COUNT", "방 인원 구성이 올바르지 않습니다."),
    INVALID_LEAVE_COUNT("INVALID_LEAVE_COUNT", "이탈 횟수는 0 이상이어야 합니다."),
    START_AT_REQUIRED("START_AT_REQUIRED", "시작 시각은 필수입니다."),
    INVALID_CLOSE_AT("INVALID_CLOSE_AT", "방 상태와 닫힌 시각이 일치하지 않습니다."),
    NOT_ROOM_PLAYER("NOT_ROOM_PLAYER", "이 방의 참가자가 아닙니다."),
    ROOM_IS_FULL("ROOM_IS_FULL", "방에 빈자리가 없습니다."),
    ROOM_IS_EMPTY("ROOM_IS_EMPTY", "남은 참가자가 없습니다."),
    ROOM_NOT_JOINABLE("ROOM_NOT_JOINABLE", "지금은 합류할 수 없는 방입니다."),
    ALREADY_ROOM_PLAYER("ALREADY_ROOM_PLAYER", "이미 이 방의 참가자입니다."),
    ALREADY_LEFT_ROOM("ALREADY_LEFT_ROOM", "이미 이 방에서 나간 참가자입니다.");
    private final String code;
    private final String message;
}
