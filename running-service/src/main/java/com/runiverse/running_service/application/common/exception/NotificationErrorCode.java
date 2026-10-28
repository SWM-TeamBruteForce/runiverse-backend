package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    // 알림 목록 커서를 풀 수 없다 — 서버가 준 값을 그대로 돌려보내지 않은 것이라 요청 검증 실패와 같게 본다
    INVALID_NOTIFICATION_CURSOR("INVALID_REQUEST", "입력값이 올바르지 않습니다.");
    private final String code;
    private final String message;
}
