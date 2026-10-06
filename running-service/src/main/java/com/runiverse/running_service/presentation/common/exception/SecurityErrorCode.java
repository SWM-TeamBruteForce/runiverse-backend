package com.runiverse.running_service.presentation.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecurityErrorCode {
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", "인증이 필요합니다."),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "액세스 토큰이 만료되었습니다."),
    TOKEN_BLOCKED("TOKEN_BLOCKED", "로그아웃된 액세스 토큰입니다."),
    INVALID_TOKEN("INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    ACCESS_DENIED("ACCESS_DENIED", "본인만 요청할 수 있습니다.");
    private final String code;
    private final String message;
}
