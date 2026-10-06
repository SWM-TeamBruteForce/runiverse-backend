package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException() {
        super(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
