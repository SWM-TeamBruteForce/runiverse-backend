package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    ALREADY_ONBOARDED("ALREADY_ONBOARDED", "이미 온보딩을 완료한 계정입니다."),
    ONBOARDING_NOT_COMPLETED("ONBOARDING_NOT_COMPLETED", "온보딩을 먼저 완료해 주세요."),
    NICKNAME_ALREADY_EXISTS("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),
    PASSWORD_NOT_SET("PASSWORD_NOT_SET", "소셜 로그인으로 가입한 계정은 비밀번호를 변경할 수 없습니다."),
    PROFILE_IMAGE_NOT_UPLOADED("PROFILE_IMAGE_NOT_UPLOADED", "업로드되지 않은 이미지입니다."),
    INVALID_PROFILE_IMAGE("INVALID_PROFILE_IMAGE", "프로필 이미지가 올바르지 않습니다."),
    ACCOUNT_DELETION_UNAVAILABLE("ACCOUNT_DELETION_UNAVAILABLE", "일시적인 오류로 탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    private final String code;
    private final String message;
}
