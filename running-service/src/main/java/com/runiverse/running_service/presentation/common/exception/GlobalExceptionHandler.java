package com.runiverse.running_service.presentation.common.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;
import com.runiverse.running_service.application.common.exception.UserErrorCode;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.observability.logging.LogTag;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.response.CooldownErrorResponse;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 유스케이스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외: {} - {}", errorCode.getCode(), errorCode.getMessage());
        markReason(request, errorCode.getCode());
        return respond(toStatus(errorCode), errorCode.getCode(), errorCode.getMessage());
    }

    // 도메인 검증 예외
    // 여기까지 왔다면 Request DTO 검증이나 유스케이스가 걸러야 할 입력을 놓친 것이라 ERROR로 남긴다
    @ExceptionHandler(com.runiverse.running_service.domain.common.exception.BusinessException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            com.runiverse.running_service.domain.common.exception.BusinessException e,
            HttpServletRequest request
    ) {
        log.error("{} 도메인 검증 실패 - code={}", LogTag.of(request), e.getErrorCode().getCode(), e);
        markReason(request, e.getErrorCode().getCode());
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage()
        );
    }

    // @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" "));
        // 입력값에는 개인정보가 섞일 수 있어 필드 이름만 남긴다
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .toList();
        log.info("{} 요청 검증 실패 - fields={}", LogTag.of(request), fields);
        markReason(request, CommonErrorCode.INVALID_REQUEST.getCode());
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_REQUEST.getCode(),
                message.isBlank() ? CommonErrorCode.INVALID_REQUEST.getMessage() : message
        );
    }

    // JSON 문법 오류 등 본문 자체를 읽지 못한 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        // 예외 메시지에는 잘못 보낸 입력값이 그대로 들어가 원인 예외의 종류만 남긴다
        log.info("{} 요청 본문 파싱 실패 - cause={}",
                LogTag.of(request), e.getMostSpecificCause().getClass().getSimpleName());
        markReason(request, CommonErrorCode.MALFORMED_REQUEST_BODY.getCode());
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.MALFORMED_REQUEST_BODY.getCode(),
                CommonErrorCode.MALFORMED_REQUEST_BODY.getMessage()
        );
    }

    // 매핑된 컨트롤러가 없는 경로 — 정적 리소스 핸들러까지 내려갔다가 여기로 온다
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        log.info("{} 경로 매칭 실패 - method={}, path={}",
                LogTag.of(request), request.getMethod(), request.getRequestURI());
        markReason(request, ResourceErrorCode.NOT_FOUND.getCode());
        return respond(
                HttpStatus.NOT_FOUND,
                ResourceErrorCode.NOT_FOUND.getCode(),
                ResourceErrorCode.NOT_FOUND.getMessage()
        );
    }

    // 예상 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e, HttpServletRequest request) {
        log.error("{} 처리하지 못한 예외", LogTag.of(request), e);
        markReason(request, CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    // 경로 변수 타입 변환 실패 (예: userId가 UUID가 아님)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request
    ) {
        log.info("{} 경로 변수 변환 실패 - param={}", LogTag.of(request), e.getName());
        markReason(request, CommonErrorCode.INVALID_REQUEST.getCode());
        return respond(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_REQUEST.getCode(),
                CommonErrorCode.INVALID_REQUEST.getMessage()
        );
    }

    // BusinessException 핸들러보다 구체적이라 Spring이 이쪽을 고른다.
    // 노출 정책을 타지 않고 바로 내보내지만, 실수로 일반 경로를 타도 마스킹되지 않게
    // EXPOSED_CODES에도 등록해 둔다
    // 쿨다운
    @ExceptionHandler(MatchCooldownException.class)
    public ResponseEntity<CooldownErrorResponse> handleMatchCooldown(MatchCooldownException e,
                                                                     HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외: {} - {}", errorCode.getCode(), errorCode.getMessage());
        markReason(request, errorCode.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CooldownErrorResponse(
                        errorCode.getCode(), errorCode.getMessage(), e.getCooldownUntil()));
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
            case MatchErrorCode code -> toStatus(code);
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
            case ACCOUNT_DELETION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
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
                 OAUTH_LOGIN_FAILED -> HttpStatus.UNAUTHORIZED;
            case OAUTH_EMAIL_NOT_PROVIDED,
                 EMAIL_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case UNSUPPORTED_PROVIDER,
                 EMAIL_VERIFICATION_NOT_FOUND,
                 INVALID_VERIFICATION_CODE -> HttpStatus.BAD_REQUEST;
            case EMAIL_VERIFICATION_COOLDOWN,
                 EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED,
                 TOO_MANY_VERIFICATION_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
            case OAUTH_PROVIDER_UNAVAILABLE,
                 EMAIL_SEND_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
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

    private HttpStatus toStatus(MatchErrorCode code) {
        return switch (code) {
            case MATCH_ALREADY_IN_PROGRESS,
                 MATCH_COOLDOWN,
                 MATCH_SLOT_CLOSED,
                 MATCH_ALREADY_STARTED -> HttpStatus.CONFLICT;
        };
    }

    // 메트릭의 실패 원인 — 응답에서 500으로 숨기는 코드도 실제 코드를 남긴다
    private void markReason(HttpServletRequest request, String code) {
        request.setAttribute(HttpRequestMetricsFilter.REASON, code);
    }
}
