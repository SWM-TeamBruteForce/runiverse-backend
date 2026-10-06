package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.common.exception.ErrorCode;

public record ErrorPayload(String code, String message, String sourceType) {

    // 봉투 단계 실패 — 유스케이스에 닿기 전이라 WS 전용 코드를 쓴다
    public static ErrorPayload of(RunningWebSocketErrorCode errorCode, String sourceType) {
        return new ErrorPayload(errorCode.getCode(), errorCode.getMessage(), sourceType);
    }

    // 유스케이스가 튕겨낸 실패 — 코드·문구는 application ErrorCode가 정본이다
    public static ErrorPayload of(ErrorCode errorCode, String sourceType) {
        return new ErrorPayload(errorCode.getCode(), errorCode.getMessage(), sourceType);
    }
}
