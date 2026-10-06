package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class InvalidCurrentPasswordException extends BusinessException {

    public InvalidCurrentPasswordException() {
        super(UserErrorCode.INVALID_CURRENT_PASSWORD);
    }
}
