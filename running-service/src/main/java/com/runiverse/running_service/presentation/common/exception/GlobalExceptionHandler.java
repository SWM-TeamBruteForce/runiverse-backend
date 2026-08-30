package com.runiverse.running_service.presentation.common.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;
import com.runiverse.running_service.application.common.exception.UserErrorCode;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 유스케이스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return respond(toStatus(errorCode), errorCode.getCode(), errorCode.getMessage());
    }

    // 도메인 검증 예외
    @ExceptionHandler(com.runiverse.running_service.domain.common.exception.BusinessException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            com.runiverse.running_service.domain.common.exception.BusinessException e
    ) {
        log.warn("도메인 예외: {} - {}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage()
        );
    }

    // @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" "));
        log.warn("요청 검증 실패: {}", message);
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_REQUEST.getCode(),
                message.isBlank() ? CommonErrorCode.INVALID_REQUEST.getMessage() : message
        );
    }

    // JSON 문법 오류 등 본문 자체를 읽지 못한 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.MALFORMED_REQUEST_BODY.getCode(),
                CommonErrorCode.MALFORMED_REQUEST_BODY.getMessage()
        );
    }

    // 예상 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    // 경로 변수 타입 변환 실패 (예: userId가 UUID가 아님)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("경로 변수 변환 실패: {}", e.getName());
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_REQUEST.getCode(),
                CommonErrorCode.INVALID_REQUEST.getMessage()
        );
    }

    // 노출 대상이 아닌 경우 전부 500으로 대체
    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        if (!ErrorExposurePolicy.isExposed(status, code)) {
            log.warn("비노출 대상이라 500으로 대체: {} {}", status.value(), code);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorExposurePolicy.masked());
        }
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    // ErrorCode가 sealed라 default 없이도 컴파일러가 누락을 잡는다
    private HttpStatus toStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case UserErrorCode code -> toStatus(code);
            case AuthErrorCode code -> toStatus(code);
            case RunningErrorCode code -> toStatus(code);
            case ResourceErrorCode code -> toStatus(code);
        };
    }

    private HttpStatus toStatus(UserErrorCode code) {
        return switch (code) {
            case PROFILE_IMAGE_NOT_UPLOADED,
                 INVALID_PROFILE_IMAGE -> HttpStatus.BAD_REQUEST;
            case INVALID_CURRENT_PASSWORD -> HttpStatus.UNAUTHORIZED;
            case ALREADY_ONBOARDED,
                 ONBOARDING_NOT_COMPLETED,
                 NICKNAME_ALREADY_EXISTS,
                 PASSWORD_NOT_SET -> HttpStatus.CONFLICT;
            // 계정 존재 여부를 숨기려고 노출하지 않는다 — ErrorExposurePolicy에서도 제외돼 500으로 응답한다
            case USER_NOT_FOUND -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private HttpStatus toStatus(ResourceErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
    }

    private HttpStatus toStatus(AuthErrorCode code) {
        return switch (code) {
            case EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS,
                 INVALID_REFRESH_TOKEN,
                 OAUTH_CODE_EXCHANGE_FAILED -> HttpStatus.UNAUTHORIZED;
            case OAUTH_EMAIL_NOT_PROVIDED,
                 EMAIL_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case UNSUPPORTED_PROVIDER,
                 EMAIL_VERIFICATION_NOT_FOUND,
                 INVALID_VERIFICATION_CODE -> HttpStatus.BAD_REQUEST;
            case EMAIL_VERIFICATION_COOLDOWN,
                 EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED,
                 TOO_MANY_VERIFICATION_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
            case EMAIL_SEND_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private HttpStatus toStatus(RunningErrorCode code) {
        return switch (code) {
            // 한 플레이어 = 최대 한 방 — 진행 중인 신청이 있으면 새로 못 연다
            case RUNNING_ALREADY_IN_PROGRESS -> HttpStatus.CONFLICT;
            // NOT_ROOM_PLAYER는 대시보드 조회(6-1·6-2)가 REST로도 던진다 — 403이 실제로 나간다.
            // 나머지 둘은 아직 WS 전용이라 이 경로로 나갈 일이 없다 — 스위치를 비워둘 수 없어 의미에 맞는 상태만 적어둔다
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_ROOM_PLAYER -> HttpStatus.FORBIDDEN;
            case INVALID_ROOM_STATE -> HttpStatus.CONFLICT;
            case RUNNING_SESSION_UNAVAILABLE,
                 RUNNING_TRACK_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
