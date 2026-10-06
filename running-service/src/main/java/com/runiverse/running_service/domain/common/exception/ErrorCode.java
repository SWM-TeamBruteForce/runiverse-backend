package com.runiverse.running_service.domain.common.exception;

public sealed interface ErrorCode permits UserErrorCode, UserOnboardingErrorCode, OauthUserErrorCode,
        DeletedUserErrorCode, RunningRoomErrorCode, RunningPlayerErrorCode, RunningMetricErrorCode,
        RunningRecordErrorCode, ScheduledJobErrorCode {

    String getCode();

    String getMessage();
}
