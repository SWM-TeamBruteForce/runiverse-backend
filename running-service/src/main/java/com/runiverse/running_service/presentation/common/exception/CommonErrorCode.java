package com.runiverse.running_service.presentation.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// application usecase에 오기 전에 생기는 오류에 대해서 작성함
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode {
    // Controller 에러
    INVALID_REQUEST("INVALID_REQUEST", "입력값이 올바르지 않습니다."),
    MALFORMED_REQUEST_BODY("MALFORMED_REQUEST_BODY", "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "지원하지 않는 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 본문 형식입니다."),
    // 앱 버전 검사 필터 에러
    APP_UPDATE_REQUIRED("APP_UPDATE_REQUIRED", "앱 업데이트가 필요합니다."),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");
    private final String code;
    private final String message;
}
