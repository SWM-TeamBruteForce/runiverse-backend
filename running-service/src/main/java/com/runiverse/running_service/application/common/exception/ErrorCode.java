package com.runiverse.running_service.application.common.exception;

public sealed interface ErrorCode permits AuthErrorCode, UserErrorCode, RunningErrorCode, MatchErrorCode,
        RoomChatErrorCode,
        ResourceErrorCode, RelationshipErrorCode, InviteErrorCode, NotificationErrorCode {

    String getCode();

    String getMessage();
}
