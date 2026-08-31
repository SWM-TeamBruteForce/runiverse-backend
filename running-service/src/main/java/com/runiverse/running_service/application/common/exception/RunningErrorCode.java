package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningErrorCode implements ErrorCode {
    RUNNING_ALREADY_IN_PROGRESS("RUNNING_ALREADY_IN_PROGRESS", "이미 진행 중인 매칭이 있습니다."),
    // 아래 셋은 WS ERROR 메시지로만 나간다 — 코드·문구를 명세의 WS 에러 표와 맞춘다
    ROOM_NOT_FOUND("ROOM_NOT_FOUND", "러닝 정보를 찾을 수 없습니다."),
    NOT_ROOM_PLAYER("NOT_ROOM_PLAYER", "이 방의 참가자가 아닙니다."),
    INVALID_ROOM_STATE("INVALID_ROOM_STATE", "지금은 시작할 수 없는 방입니다."),
    // 외부 저장소 장애로 세션을 등록하지 못한 경우 — 클라는 잠시 뒤 RUNNING_START를 재시도한다
    RUNNING_SESSION_UNAVAILABLE("RUNNING_SESSION_UNAVAILABLE", "일시적인 오류로 러닝을 시작하지 못했습니다."),
    // 좌표를 저장하지 못한 경우 — 러닝은 계속된다. 클라는 로컬 트랙을 지우지 않고 재연결로 복구한다
    RUNNING_TRACK_UNAVAILABLE("RUNNING_TRACK_UNAVAILABLE", "위치 정보를 저장하지 못했습니다.");
    private final String code;
    private final String message;
}
