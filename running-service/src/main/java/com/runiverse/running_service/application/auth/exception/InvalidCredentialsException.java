package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
