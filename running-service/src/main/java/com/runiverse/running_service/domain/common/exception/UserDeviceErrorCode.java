package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserDeviceErrorCode implements ErrorCode {
    DEVICE_ID_REQUIRED("DEVICE_ID_REQUIRED", "기기 ID는 필수입니다."),
    INVALID_DEVICE_ID_LENGTH("INVALID_DEVICE_ID_LENGTH", "기기 ID는 100자 이하여야 합니다."),
    PUSH_TOKEN_REQUIRED("PUSH_TOKEN_REQUIRED", "푸시 토큰은 필수입니다.");
    private final String code;
    private final String message;
}
