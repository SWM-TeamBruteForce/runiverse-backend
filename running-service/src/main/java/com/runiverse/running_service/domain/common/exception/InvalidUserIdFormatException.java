package com.runiverse.running_service.domain.common.exception;

public class InvalidUserIdFormatException extends BusinessException {

    public InvalidUserIdFormatException() {
        super(UserErrorCode.INVALID_USER_ID_FORMAT);
    }
}
