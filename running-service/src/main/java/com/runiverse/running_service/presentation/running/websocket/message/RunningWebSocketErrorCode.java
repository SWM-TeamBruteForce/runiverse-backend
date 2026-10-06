package com.runiverse.running_service.presentation.running.websocket.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningWebSocketErrorCode {
    // 봉투 자체를 못 읽음 — REST의 MALFORMED_REQUEST_BODY에 대응
    MALFORMED_MESSAGE("MALFORMED_MESSAGE", "메시지 형식이 올바르지 않습니다."),
    MISSING_MESSAGE_TYPE("MISSING_MESSAGE_TYPE", "메시지 타입이 없습니다."),
    UNSUPPORTED_MESSAGE_TYPE("UNSUPPORTED_MESSAGE_TYPE", "지원하지 않는 메시지 타입입니다."),
    INVALID_REQUEST("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."),
    // 형식은 맞지만 RUNNING_START를 거치지 않아 서버에 정해진 방이 없다
    RUNNING_NOT_STARTED("RUNNING_NOT_STARTED", "러닝이 시작되지 않았습니다."),
    // 마지막 그물 — 계약에 없는 예외가 새면 내부 사정을 흘리지 않고 이 고정 문구로 답한다.
    // 코드·문구는 REST 마스킹(CommonErrorCode)과 같다
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");
    private final String code;
    private final String message;
}
