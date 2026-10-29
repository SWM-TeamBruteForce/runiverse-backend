package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VersionNameErrorCode implements ErrorCode {
    VERSION_NAME_REQUIRED("VERSION_NAME_REQUIRED", "버전 이름은 필수입니다."),
    INVALID_VERSION_NAME_FORMAT("INVALID_VERSION_NAME_FORMAT", "버전 이름은 MAJOR.MINOR.PATCH 형식이어야 합니다.");
    private final String code;
    private final String message;
}
