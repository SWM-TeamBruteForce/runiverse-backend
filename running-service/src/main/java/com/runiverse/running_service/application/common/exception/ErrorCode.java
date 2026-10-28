package com.runiverse.running_service.application.common.exception;

public sealed interface ErrorCode permits AuthErrorCode, UserErrorCode, RunningErrorCode, MatchErrorCode,
        ResourceErrorCode, RelationshipErrorCode, InviteErrorCode {

    String getCode();

    String getMessage();
}
