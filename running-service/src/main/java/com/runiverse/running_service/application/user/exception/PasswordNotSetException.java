package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class PasswordNotSetException extends BusinessException {

    public PasswordNotSetException() {
        super(UserErrorCode.PASSWORD_NOT_SET);
    }
}
