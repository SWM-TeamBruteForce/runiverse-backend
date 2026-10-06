package com.runiverse.running_service.application.common.exception;

public sealed interface ErrorCode permits AuthErrorCode, UserErrorCode, RunningErrorCode, MatchErrorCode,
        ResourceErrorCode {

    String getCode();

    String getMessage();
}
