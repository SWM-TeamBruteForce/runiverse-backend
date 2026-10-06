package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduledJobErrorCode implements ErrorCode {
    INVALID_SCHEDULED_JOB_ID("INVALID_SCHEDULED_JOB_ID", "예약 작업 ID가 올바르지 않습니다."),
    INVALID_JOB_TARGET("INVALID_JOB_TARGET", "예약 작업의 대상이 올바르지 않습니다."),
    EXECUTE_AT_REQUIRED("EXECUTE_AT_REQUIRED", "예약 실행 시각은 필수입니다."),
    INVALID_SENT_AT("INVALID_SENT_AT", "발화 여부와 발화 시각이 일치하지 않습니다."),
    SCHEDULED_JOB_ALREADY_SENT("SCHEDULED_JOB_ALREADY_SENT", "이미 실행된 예약 작업입니다.");
    private final String code;
    private final String message;
}
