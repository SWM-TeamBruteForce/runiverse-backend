package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class PasswordNotSetException extends BusinessException {

    public PasswordNotSetException() {
        super(UserErrorCode.PASSWORD_NOT_SET);
    }
}
