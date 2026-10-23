package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformErrorCode implements ErrorCode {
    PLATFORM_REQUIRED("PLATFORM_REQUIRED", "플랫폼은 필수입니다."),
    UNSUPPORTED_PLATFORM("UNSUPPORTED_PLATFORM", "플랫폼은 IOS 또는 ANDROID이어야 합니다.");
    private final String code;
    private final String message;
}
