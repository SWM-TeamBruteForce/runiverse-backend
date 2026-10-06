package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeletedUserErrorCode implements ErrorCode {
    BMI_REQUIRED("BMI_REQUIRED", "BMI는 null일 수 없습니다."),
    BMI_OUT_OF_RANGE("BMI_OUT_OF_RANGE", "BMI는 0보다 커야 합니다."),
    LOGIN_TYPE_REQUIRED("LOGIN_TYPE_REQUIRED", "가입 수단은 필수입니다."),
    JOINED_AT_REQUIRED("JOINED_AT_REQUIRED", "가입 시각은 필수입니다."),
    ONBOARDING_SNAPSHOT_INCOMPLETE("ONBOARDING_SNAPSHOT_INCOMPLETE",
            "온보딩 스냅샷은 전부 있거나 전부 없어야 합니다.");
    private final String code;
    private final String message;
}
