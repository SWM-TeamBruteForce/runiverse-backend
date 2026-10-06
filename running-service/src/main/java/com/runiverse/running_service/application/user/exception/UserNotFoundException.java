package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
