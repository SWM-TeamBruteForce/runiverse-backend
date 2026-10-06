package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OauthUserErrorCode implements ErrorCode {
    PROVIDER_REQUIRED("PROVIDER_REQUIRED", "소셜 로그인 제공자는 필수입니다"),
    UNSUPPORTED_PROVIDER("UNSUPPORTED_PROVIDER", "지원하지 않는 소셜 로그인입니다."),
    PROVIDER_ID_REQUIRED("PROVIDER_ID_REQUIRED", "소셜 계정 식별자를 가져오지 못했습니다."),
    PROVIDER_ID_TOO_LONG("PROVIDER_ID_TOO_LONG", "소셜 계정 식별자 길이가 허용 범위를 벗어났습니다."),
    OAUTH_ALREADY_LINKED("OAUTH_ALREADY_LINKED", "이미 연결된 소셜 계정입니다."),
    OAUTH_NOT_LINKED("OAUTH_NOT_LINKED", "연결되지 않은 소셜 계정입니다."),
    LAST_SIGN_IN_METHOD("LAST_SIGN_IN_METHOD", "마지막 로그인 수단은 해제할 수 없습니다.");
    private final String code;
    private final String message;
}
