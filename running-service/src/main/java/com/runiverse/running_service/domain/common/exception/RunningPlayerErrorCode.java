package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningPlayerErrorCode implements ErrorCode {
    INVALID_RUNNING_PLAYER_ID("INVALID_RUNNING_PLAYER_ID", "러닝 참가자 ID가 올바르지 않습니다."),
    INVALID_PLAYER_STATUS_TRANSITION("INVALID_PLAYER_STATUS_TRANSITION", "허용되지 않는 참가자 상태 변경입니다."),
    INVALID_DESIRED_PLAYER_COUNT("INVALID_DESIRED_PLAYER_COUNT", "희망 매칭 인원이 올바르지 않습니다."),
    PLAYER_ALREADY_LEFT("PLAYER_ALREADY_LEFT", "이미 종료된 참가 신청입니다."),
    START_AT_REQUIRED("START_AT_REQUIRED", "시작 시각은 필수입니다.");
    private final String code;
    private final String message;
}
