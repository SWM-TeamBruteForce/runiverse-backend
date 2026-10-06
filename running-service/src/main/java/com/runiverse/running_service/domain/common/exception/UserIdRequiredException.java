package com.runiverse.running_service.domain.common.exception;

public class UserIdRequiredException extends BusinessException {

    public UserIdRequiredException() {
        super(UserErrorCode.USER_ID_REQUIRED);
    }
}
