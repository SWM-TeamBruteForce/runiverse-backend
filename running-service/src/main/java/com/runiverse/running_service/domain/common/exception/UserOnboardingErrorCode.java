package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserOnboardingErrorCode implements ErrorCode {
    NICKNAME_REQUIRED("NICKNAME_REQUIRED", "닉네임은 필수입니다."),
    INVALID_NICKNAME_FORMAT("INVALID_NICKNAME_FORMAT", "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."),
    INVALID_NICKNAME_LENGTH("INVALID_NICKNAME_LENGTH", "닉네임은 2자 이상 16자 이하여야 합니다."),
    GENDER_REQUIRED("GENDER_REQUIRED", "성별은 필수입니다."),
    UNSUPPORTED_GENDER("UNSUPPORTED_GENDER", "지원하지 않는 성별입니다."),
    BIRTHDAY_REQUIRED("BIRTHDAY_REQUIRED", "생년월일은 필수입니다."),
    INVALID_BIRTHDAY("INVALID_BIRTHDAY", "올바른 생년월일이 아닙니다."),
    AVG_PACE_OUT_OF_RANGE("AVG_PACE_OUT_OF_RANGE", "평균 페이스가 허용 범위를 벗어났습니다."),
    WEIGHT_REQUIRED("WEIGHT_REQUIRED", "몸무게는 필수입니다."),
    WEIGHT_OUT_OF_RANGE("WEIGHT_OUT_OF_RANGE", "몸무게가 허용 범위를 벗어났습니다."),
    HEIGHT_REQUIRED("HEIGHT_REQUIRED", "키는 필수입니다."),
    HEIGHT_OUT_OF_RANGE("HEIGHT_OUT_OF_RANGE", "키가 허용 범위를 벗어났습니다."),
    ONBOARDING_ALREADY_COMPLETED("ONBOARDING_ALREADY_COMPLETED", "이미 온보딩을 완료했습니다."),
    ONBOARDING_NOT_COMPLETED("ONBOARDING_NOT_COMPLETED", "온보딩을 먼저 완료해야 합니다.");
    private final String code;
    private final String message;
}
