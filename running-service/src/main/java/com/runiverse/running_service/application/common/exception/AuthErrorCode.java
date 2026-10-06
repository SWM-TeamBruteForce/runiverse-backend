package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다. 로그인해 주세요."),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."),
    UNSUPPORTED_PROVIDER("UNSUPPORTED_PROVIDER", "지원하지 않는 로그인 제공자입니다."),
    OAUTH_LOGIN_FAILED("OAUTH_LOGIN_FAILED","소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    OAUTH_PROVIDER_UNAVAILABLE("OAUTH_PROVIDER_UNAVAILABLE", "소셜 로그인에 잠시 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    OAUTH_EMAIL_NOT_PROVIDED("OAUTH_EMAIL_NOT_PROVIDED", "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."),

    // 이메일 인증과 관련된 에러코드
    EMAIL_VERIFICATION_COOLDOWN("EMAIL_VERIFICATION_COOLDOWN", "인증 메일을 방금 보냈습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED("EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED", "하루 인증 메일 발송 횟수를 초과했습니다."),
    EMAIL_VERIFICATION_NOT_FOUND("EMAIL_VERIFICATION_NOT_FOUND", "인증 코드가 만료되었습니다. 다시 요청해 주세요."),
    INVALID_VERIFICATION_CODE("INVALID_VERIFICATION_CODE", "인증 코드가 올바르지 않습니다."),
    TOO_MANY_VERIFICATION_ATTEMPTS("TOO_MANY_VERIFICATION_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 코드를 다시 요청해 주세요."),
    EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", "이메일 인증이 만료되었습니다. 다시 인증해 주세요."),
    EMAIL_SEND_FAILED("EMAIL_SEND_FAILED", "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    private final String code;
    private final String message;
}
