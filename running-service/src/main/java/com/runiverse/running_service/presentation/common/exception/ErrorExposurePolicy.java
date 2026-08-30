package com.runiverse.running_service.presentation.common.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;
import com.runiverse.running_service.application.common.exception.UserErrorCode;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;

import java.util.Set;

// 그대로 노출하는 에러 코드 추가
public final class ErrorExposurePolicy {

    private static final Set<String> EXPOSED_CODES = Set.of(
            AuthErrorCode.EMAIL_ALREADY_EXISTS.getCode(),
            UserErrorCode.NICKNAME_ALREADY_EXISTS.getCode(),
            UserErrorCode.INVALID_CURRENT_PASSWORD.getCode(),
            UserErrorCode.PASSWORD_NOT_SET.getCode(),
            UserErrorCode.ALREADY_ONBOARDED.getCode(),
            UserErrorCode.ONBOARDING_NOT_COMPLETED.getCode(),
            UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED.getCode(),
            ResourceErrorCode.NOT_FOUND.getCode(),
            UserErrorCode.INVALID_PROFILE_IMAGE.getCode(),
            AuthErrorCode.INVALID_CREDENTIALS.getCode(),
            AuthErrorCode.OAUTH_CODE_EXCHANGE_FAILED.getCode(),
            AuthErrorCode.OAUTH_EMAIL_NOT_PROVIDED.getCode(),
            AuthErrorCode.UNSUPPORTED_PROVIDER.getCode(),
            AuthErrorCode.INVALID_REFRESH_TOKEN.getCode(),
            SecurityErrorCode.TOKEN_EXPIRED.getCode(),
            SecurityErrorCode.TOKEN_BLOCKED.getCode(),
            SecurityErrorCode.INVALID_TOKEN.getCode(),
            SecurityErrorCode.AUTHENTICATION_REQUIRED.getCode(),
            SecurityErrorCode.ACCESS_DENIED.getCode(),
            // 이메일 인증 관련한 에러는 그대로 보낸다.
            AuthErrorCode.EMAIL_VERIFICATION_COOLDOWN.getCode(),
            AuthErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED.getCode(),
            AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND.getCode(),
            AuthErrorCode.INVALID_VERIFICATION_CODE.getCode(),
            AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS.getCode(),
            AuthErrorCode.EMAIL_NOT_VERIFIED.getCode(),
            AuthErrorCode.EMAIL_SEND_FAILED.getCode(),
            RunningErrorCode.RUNNING_ALREADY_IN_PROGRESS.getCode(),
            // 6-1이 이 코드를 REST로 던진다 — 방 참가자가 아니면 403으로 그대로 나간다
            RunningErrorCode.NOT_ROOM_PLAYER.getCode()
            // ROOM_NOT_FOUND·INVALID_ROOM_STATE는 아직 WS ERROR 메시지로만 나간다.
            // 6-1의 404는 ResourceErrorCode.NOT_FOUND를 쓴다(api-spec 6-1)
    );

    private ErrorExposurePolicy() {
    } // 다른 곳에서 사용하지 못하도록

    // 400 에러나 내가 노출을 허용한 에러에 경우를 검증하는 메서드
    public static boolean isExposed(HttpStatus status, String code) {
        return status == HttpStatus.BAD_REQUEST || EXPOSED_CODES.contains(code);
    }

    // 그냥 평범한 에러는 모두 500서버 에러로 넘길 예정
    public static ErrorResponse masked() {
        return new ErrorResponse(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );
    }
}
